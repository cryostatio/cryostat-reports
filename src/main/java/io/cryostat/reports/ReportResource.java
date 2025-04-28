/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.reports;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import io.cryostat.core.reports.InterruptibleReportGenerator;
import io.cryostat.core.reports.InterruptibleReportGenerator.AnalysisResult;
import io.cryostat.core.util.RuleFilterParser;
import io.cryostat.libcryostat.sys.FileSystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.Blocking;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.openjdk.jmc.common.io.IOToolkit;
import org.openjdk.jmc.flightrecorder.rules.IRule;

@Path("/")
public class ReportResource {

    static final String SINGLETHREAD_PROPERTY =
            "org.openjdk.jmc.flightrecorder.parser.singlethreaded";

    @ConfigProperty(name = "io.cryostat.reports.memory-factor", defaultValue = "0")
    long memoryFactor;

    @ConfigProperty(name = "io.cryostat.reports.timeout", defaultValue = "29000")
    String timeoutMs;

    @ConfigProperty(name = "cryostat.storage.base-uri")
    Optional<String> storageBase;

    @ConfigProperty(name = "cryostat.storage.auth-method")
    Optional<String> storageAuthMethod;

    @ConfigProperty(name = "cryostat.storage.auth")
    Optional<String> storageAuth;

    @ConfigProperty(name = "cryostat.storage.tls-version")
    String storageTlsVersion;

    @ConfigProperty(name = "cryostat.storage.ignore-tls")
    boolean storageTlsIgnore;

    @ConfigProperty(name = "cryostat.storage.verify-hostname")
    boolean storageHostnameVerify;

    @ConfigProperty(name = "cryostat.storage.tls.ca.path")
    Optional<java.nio.file.Path> storageCaPath;

    @ConfigProperty(name = "cryostat.storage.tls.cert.path")
    Optional<java.nio.file.Path> storageCertPath;

    @Inject InterruptibleReportGenerator generator;
    @Inject FileSystem fs;
    @Inject ObjectMapper mapper;
    @Inject RuleFilterParser rfp;
    @Inject Logger logger;

    void onStart(@Observes StartupEvent ev) {
        logger.infof(
                "CPUs: %d singlethread: %b maxMemory: %dM memoryFactor: %s timeout: %sms",
                Runtime.getRuntime().availableProcessors(),
                Boolean.getBoolean(SINGLETHREAD_PROPERTY),
                Runtime.getRuntime().maxMemory() / (1024 * 1024),
                memoryFactor,
                timeoutMs);
    }

    @Path("health")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public void healthCheck() {}

    @Blocking
    @Path("remote_report")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @POST
    public String getReportFromPresigned(RoutingContext ctx, @BeanParam PresignedFormData form)
            throws IOException, URISyntaxException {
        // TODO queue these requests so we don't overload ourselves, in particular by reading
        // multiple JFR files into memory at once for analysis. We should process these serially
        // from the queue. If we are getting overloaded then our response time to each subsequent
        // request will continue to grow unbounded, so at some point we should stop accepting
        // requests when the queue is too long.
        // Since this is a @Blocking method that runs on a worker thread pool, can we implement this
        // serial queueing behaviour by simply synchronizing on a shared singleton resource ex. the
        // generator instance?
        // A better long-term solution would be to use a shared messaging queue between Cryostat and
        // the report generators, so that Cryostat can put up a URL for a presigned recording to be
        // processed and a free report generator can claim that work item and then post back the
        // report response
        long timeout = TimeUnit.MILLISECONDS.toNanos(Long.parseLong(timeoutMs));
        long start = System.nanoTime();

        if (storageBase.isEmpty()) {
            throw new ServerErrorException(Response.Status.BAD_GATEWAY);
        }

        UriBuilder uriBuilder =
                UriBuilder.newInstance()
                        .uri(new URI(storageBase.get()))
                        .path(form.path)
                        .replaceQuery(form.query);
        URI downloadUri = uriBuilder.build();
        logger.infov("Attempting to download presigned recording from {0}", downloadUri);
        HttpURLConnection httpConn = (HttpURLConnection) downloadUri.toURL().openConnection();
        httpConn.setRequestMethod("GET");
        if (httpConn instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConn = (HttpsURLConnection) httpConn;
            if (storageTlsIgnore) {
                try {
                    httpsConn.setSSLSocketFactory(
                            ignoreSslContext(storageTlsVersion).getSocketFactory());
                } catch (Exception e) {
                    logger.error(e);
                    throw new InternalServerErrorException(e);
                }
            } else if (storageCaPath.isPresent() || storageCertPath.isPresent()) {
                if (!(storageCaPath.isPresent() && storageCertPath.isPresent())) {
                    Exception e =
                            new IllegalStateException(
                                    String.format(
                                            "%s and %s must be both set or both unset",
                                            "cryostat.storage.tls.ca.path",
                                            "cryostat.storage.tls.cert.path"));
                    logger.error(e);
                    throw new InternalServerErrorException(e);
                }
                try {
                    httpsConn.setSSLSocketFactory(
                            trustSslCertContext(storageCaPath.get(), storageCertPath.get())
                                    .getSocketFactory());
                } catch (Exception e) {
                    logger.error(e);
                    throw new InternalServerErrorException(e);
                }
            }
            if (!storageHostnameVerify) {
                httpsConn.setHostnameVerifier((hostname, session) -> true);
            }
        }
        if (storageAuth.isPresent() && storageAuth.isPresent()) {
            httpConn.setRequestProperty(
                    "Authorization",
                    String.format("%s %s", storageAuthMethod.get(), storageAuth.get()));
        }

        assertContentLength(httpConn.getContentLengthLong());
        try (var stream = httpConn.getInputStream()) {

            Predicate<IRule> predicate = rfp.parse(form.filter);
            Future<Map<String, AnalysisResult>> evalMapFuture = null;

            evalMapFuture = generator.generateEvalMapInterruptibly(stream, predicate);
            long elapsed = System.nanoTime() - start;
            ctxHelper(ctx, evalMapFuture);
            return mapper.writeValueAsString(
                    evalMapFuture.get(timeout - elapsed, TimeUnit.NANOSECONDS));
        } catch (ExecutionException | InterruptedException e) {
            throw new InternalServerErrorException(e);
        } catch (TimeoutException e) {
            throw new ServerErrorException(Response.Status.GATEWAY_TIMEOUT, e);
        } finally {
            httpConn.disconnect();
        }
    }

    @Blocking
    @Path("report")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @POST
    public String getReport(RoutingContext ctx, @BeanParam RecordingFormData form)
            throws IOException {
        FileUpload upload = form.file;

        Pair<java.nio.file.Path, Pair<Long, Long>> uploadResult = handleUpload(upload);
        java.nio.file.Path file = uploadResult.getLeft();
        long timeout = TimeUnit.MILLISECONDS.toNanos(Long.parseLong(timeoutMs));
        long start = uploadResult.getRight().getLeft();
        long elapsed = uploadResult.getRight().getRight();

        Predicate<IRule> predicate = rfp.parse(form.filter);
        Future<Map<String, AnalysisResult>> evalMapFuture = null;

        try (var stream = fs.newInputStream(file)) {
            evalMapFuture = generator.generateEvalMapInterruptibly(stream, predicate);
            ctxHelper(ctx, evalMapFuture);
            return mapper.writeValueAsString(
                    evalMapFuture.get(timeout - elapsed, TimeUnit.NANOSECONDS));
        } catch (ExecutionException | InterruptedException e) {
            throw new InternalServerErrorException(e);
        } catch (TimeoutException e) {
            throw new ServerErrorException(Response.Status.GATEWAY_TIMEOUT, e);
        } finally {
            cleanupHelper(evalMapFuture, file, upload.fileName(), start);
        }
    }

    private void assertContentLength(long length) {
        if (memoryFactor <= 0) {
            return;
        }
        if (length <= 0) {
            logger.debugv("Request file has indeterminate length");
            return;
        }
        logger.debugv("Request file has size {0} bytes", length);
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long availableMemory = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory();
        long maxHandleableSize = availableMemory / memoryFactor;
        if (length > maxHandleableSize) {
            logger.warnv(
                    "Rejecting request for file of {0} bytes. Estimated maximum handleable size is"
                            + " {1} bytes.",
                    length, maxHandleableSize);
            throw new ClientErrorException(Response.Status.REQUEST_ENTITY_TOO_LARGE);
        }
    }

    private Pair<java.nio.file.Path, Pair<Long, Long>> handleUpload(FileUpload upload)
            throws IOException {
        java.nio.file.Path file = upload.uploadedFile();
        long timeout = TimeUnit.MILLISECONDS.toNanos(Long.parseLong(timeoutMs));
        long start = System.nanoTime();
        long now = start;
        long elapsed = 0;

        logger.infof("Received request for %s (%d bytes)", upload.fileName(), upload.size());

        if (IOToolkit.isCompressedFile(file.toFile())) {
            file = decompress(file);
            now = System.nanoTime();
            elapsed = now - start;
            logger.infof(
                    "%s was compressed. Decompressed size: %d bytes. Decompression took %dms",
                    upload.fileName(),
                    file.toFile().length(),
                    TimeUnit.NANOSECONDS.toMillis(elapsed));
        }

        assertContentLength(file.toFile().length());

        now = System.nanoTime();
        elapsed = now - start;
        if (elapsed > timeout) {
            throw new ServerErrorException(Response.Status.GATEWAY_TIMEOUT);
        }
        return Pair.of(file, Pair.of(start, elapsed));
    }

    private void ctxHelper(RoutingContext ctx, Future<?> ff) {
        ctx.response()
                .exceptionHandler(
                        e -> {
                            logger.error(e);
                            ff.cancel(true);
                        });
        ctx.request()
                .exceptionHandler(
                        e -> {
                            logger.error(e);
                            ff.cancel(true);
                        });
        ctx.addEndHandler().onComplete(ar -> ff.cancel(true));
    }

    private void cleanupHelper(
            Future<?> future, java.nio.file.Path file, String fileName, long start)
            throws IOException {
        if (future != null) {
            future.cancel(true);
        }
        boolean deleted = fs.deleteIfExists(file);
        if (deleted) {
            logger.infof("Deleted %s", file);
        } else {
            logger.infof("Failed to delete %s", file);
        }
        logger.infof(
                "Completed request for %s after %dms",
                fileName, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }

    private java.nio.file.Path decompress(java.nio.file.Path file) throws IOException {
        java.nio.file.Path tmp = Files.createTempFile(null, null);
        try (var stream = IOToolkit.openUncompressedStream(file.toFile())) {
            fs.copy(stream, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } finally {
            fs.deleteIfExists(file);
        }
    }

    private static SSLContext ignoreSslContext(String tlsVersion) throws Exception {
        SSLContext sslContext = SSLContext.getInstance(tlsVersion);
        sslContext.init(
                null, new X509TrustManager[] {new X509TrustAllManager()}, new SecureRandom());
        return sslContext;
    }

    private static SSLContext trustSslCertContext(
            java.nio.file.Path caPath, java.nio.file.Path certPath)
            throws IOException,
                    KeyStoreException,
                    KeyManagementException,
                    CertificateException,
                    NoSuchAlgorithmException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        try (InputStream ca = new FileInputStream(caPath.toFile());
                InputStream cert = new FileInputStream(certPath.toFile()); ) {
            keyStore.load(null, null);
            keyStore.setCertificateEntry("storage-ca", certFactory.generateCertificate(ca));
            keyStore.setCertificateEntry("storage-tls", certFactory.generateCertificate(cert));

            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            SSLContext sslCtx = SSLContext.getInstance("TLSv1.2");
            sslCtx.init(null, trustManagerFactory.getTrustManagers(), null);

            return sslCtx;
        }
    }

    private static final class X509TrustAllManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {}

        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
    }
}

package io.cryostat.reports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.cryostat.core.reports.ReportGenerator;
import io.cryostat.core.sys.FileSystem;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.Blocking;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.openjdk.jmc.common.io.IOToolkit;

@Path("/")
public class ReportResource {

    private static final String SINGLETHREAD_PROPERTY =
            "org.openjdk.jmc.flightrecorder.parser.singlethreaded";

    @Inject Logger logger;
    @Inject ReportGenerator generator;
    @Inject FileSystem fs;

    void onStart(@Observes StartupEvent ev) {
        logger.infof(
                "CPUs: %d singlethread: %b maxMemory: %dM",
                Runtime.getRuntime().availableProcessors(),
                Boolean.getBoolean(SINGLETHREAD_PROPERTY),
                Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }

    @Path("health")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public void healthCheck() {}

    @Blocking
    @Path("report")
    @POST
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public String addRecording(@MultipartForm RecordingFormData form) throws IOException {
        FileUpload upload = form.file;
        java.nio.file.Path file = upload.uploadedFile();
        long start = System.nanoTime();
        logger.infof("Received request for %s (%d bytes)", upload.fileName(), upload.size());
        if (IOToolkit.isCompressedFile(file.toFile())) {
            file = decompress(file);
            logger.infof(
                    "%s was compressed. Decompressed size: %d bytes. Decompression took %dms",
                    upload.fileName(),
                    file.toFile().length(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
        try (var stream = fs.newInputStream(file)) {
            return generator.generateReport(stream);
        } finally {
            boolean deleted = fs.deleteIfExists(file);
            if (deleted) {
                logger.infof("Deleted %s", file);
            } else {
                logger.infof("Failed to delete %s", file);
            }
            logger.infof(
                    "Completed request for %s after %dms",
                    upload.fileName(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
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
}

package io.cryostat.reports;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.cryostat.core.reports.ReportGenerator;
import io.cryostat.core.sys.FileSystem;

import io.smallrye.common.annotation.Blocking;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/")
public class ReportResource {

    private final Logger logger = Logger.getLogger(getClass());

    private final ReportGenerator generator;
    private final FileSystem fs;

    @Inject
    ReportResource(ReportGenerator generator, FileSystem fs) {
        this.generator = generator;
        this.fs = fs;
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
        long start = System.nanoTime();
        logger.infof("Received request for %s (%d bytes)", upload.fileName(), upload.size());
        try (var stream = new BufferedInputStream(fs.newInputStream(upload.uploadedFile()))) {
            return generator.generateReport(stream);
        } finally {
            fs.deleteIfExists(form.file.uploadedFile());
            long end = System.nanoTime();
            logger.infof(
                    "Completed request for %s after %dms",
                    upload.fileName(), TimeUnit.NANOSECONDS.toMillis(end - start));
        }
    }
}
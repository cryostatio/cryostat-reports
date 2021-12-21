package io.cryostat;

import static io.restassured.RestAssured.given;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ReportResourceTest {

    @Test
    public void testHealthEndpoint() {
        given()
          .when().get("/health")
          .then()
             .statusCode(204);
    }

    @Test
    public void testReportEndpoint() throws URISyntaxException {
        File jfr = Paths.get(getClass().getResource("/profiling_sample.jfr").toURI()).toFile();
        given()
            .contentType("multipart/form-data")
            .multiPart("file", jfr)
            .when()
            .post("/report")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(Matchers.is(Matchers.not(Matchers.emptyOrNullString())))
            ;
    }

}

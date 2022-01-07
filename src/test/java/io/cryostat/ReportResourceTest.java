package io.cryostat;

import static io.restassured.RestAssured.given;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ReportResourceTest {

    @Test
    public void testHealthEndpoint() {
        given().when().get("/health").then().statusCode(204);
    }

    @Test
    public void testReportEndpoint() throws URISyntaxException {
        File jfr = Paths.get(getClass().getResource("/profiling_sample.jfr").toURI()).toFile();
        String response =
                given().contentType("multipart/form-data")
                        .multiPart("file", jfr)
                        .when()
                        .post("/report")
                        .then()
                        .statusCode(200)
                        .contentType("text/html")
                        .body(Matchers.is(Matchers.not(Matchers.emptyOrNullString())))
                        .extract()
                        .asString();

        Document doc = Jsoup.parse(response, "UTF-8");

        Elements head = doc.getElementsByTag("head");
        Elements titles = head.first().getElementsByTag("title");
        Elements body = doc.getElementsByTag("body");

        MatcherAssert.assertThat("Expected one <head>", head.size(), Matchers.equalTo(1));
        MatcherAssert.assertThat("Expected one <title>", titles.size(), Matchers.equalTo(1));
        MatcherAssert.assertThat(
                titles.get(0).html(), Matchers.equalTo("Automated Analysis Result Overview"));
        MatcherAssert.assertThat("Expected one <body>", body.size(), Matchers.equalTo(1));
    }

    @Test
    public void testReportEndpointWithCompressedFile() throws URISyntaxException {
        File jfr = Paths.get(getClass().getResource("/profiling_sample.jfr.gz").toURI()).toFile();
        String response =
                given().contentType("multipart/form-data")
                        .multiPart("file", jfr)
                        .when()
                        .post("/report")
                        .then()
                        .statusCode(200)
                        .contentType("text/html")
                        .body(Matchers.is(Matchers.not(Matchers.emptyOrNullString())))
                        .extract()
                        .asString();

        Document doc = Jsoup.parse(response, "UTF-8");

        Elements head = doc.getElementsByTag("head");
        Elements titles = head.first().getElementsByTag("title");
        Elements body = doc.getElementsByTag("body");

        MatcherAssert.assertThat("Expected one <head>", head.size(), Matchers.equalTo(1));
        MatcherAssert.assertThat("Expected one <title>", titles.size(), Matchers.equalTo(1));
        MatcherAssert.assertThat(
                titles.get(0).html(), Matchers.equalTo("Automated Analysis Result Overview"));
        MatcherAssert.assertThat("Expected one <body>", body.size(), Matchers.equalTo(1));
    }
}

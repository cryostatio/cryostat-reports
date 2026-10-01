# cryostat-reports

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## API

This microservice exposes only one API endpoint: `POST /report`. This expects `multipart/form-data`,
with a form field named `file` containing a JFR binary file (`application/octet-stream`). The response
is an Automated Analysis Report in `text/html` format. The uploaded file is not preserved and no
caching is performed - this is expected to be handled by the "parent" Cryostat application that is
sending the JFR binary data.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

An OCI image tagged as `quay.io/cryostatio/cryostat-reports` will also be built using `podman`
and loaded into your local `podman` image registry.

If a Quarkus build failure is encountered due to being unable to build a Docker image, then:
```shell script
sudo dnf install podman-docker
```

**Note**: If `docker` is already installed, then starting the `docker` service will solve the issue.

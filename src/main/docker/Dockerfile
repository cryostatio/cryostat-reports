####
# This Dockerfile is used in order to build a container that runs the Quarkus application
#
# Before building the container image run:
#
# ./mvnw package
#
# Then, build the image with:
#
# docker build -f src/main/docker/Dockerfile -t quarkus/cryostat-reports .
#
# Then run the container using:
#
# docker run -i --rm -p 8080:8080 quarkus/cryostat-reports
#
# If you want to include the debug port into your docker image
# you will have to expose the debug port (default 5005) like this :  EXPOSE 8080 5005
#
# Then run the container using :
#
# docker run -i --rm -p 8080:8080 -p 5005:5005 -e JAVA_DEBUG="true" quarkus/cryostat-reports
#
###
FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24-3.1790283192@sha256:c8bcc76dfc2ccf122bb7e2b82db7ae9de99a5276e495bcde9efbfa938dd5e3f7

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 target/quarkus-app/*.jar /deployments/
COPY --chown=185 target/quarkus-app/app/ /deployments/app/
COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/

ENV AB_JOLOKIA_OFF=""
ENV JAVA_OPTS_APPEND="-XX:-ExitOnOutOfMemoryError -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

EXPOSE 8080
USER 185

#!/bin/sh

set -x
set -e

if [ -z "${CPUS}" ]; then
    CPUS=1
fi

if [ "${CPUS}" -gt 1 ]; then
    SINGLETHREAD_JFR_PARSE="false"
else
    SINGLETHREAD_JFR_PARSE="true"
fi

if [ -z "${MEMORY}" ]; then
    MEMORY="512M"
fi

if [ -z "${MEMORY_FACTOR}" ]; then
    MEMORY_FACTOR=10
fi

if [ -z "${TIMEOUT}" ]; then
    TIMEOUT=30000
fi

podman run \
    --name cryostat-reports \
    --user 0 \
    --pod cryostat-pod \
    --cpus "${CPUS}" \
    --memory "${MEMORY}" \
    --env JAVA_OPTIONS="-XX:ActiveProcessorCount=${CPUS} -XX:+PrintCommandLineFlags -Dorg.openjdk.jmc.flightrecorder.parser.singlethreaded=${SINGLETHREAD_JFR_PARSE} -Dio.cryostat.reports.memory-factor=${MEMORY_FACTOR} -Dio.cryostat.reports.timeout=${TIMEOUT} -Dcom.sun.management.jmxremote.port=7878 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -javaagent:/deployments/app/cryostat-agent.jar" \
    --env QUARKUS_HTTP_PORT=10005 \
    --env CRYOSTAT_AGENT_APP_NAME="cryostat-reports" \
    --env CRYOSTAT_AGENT_CALLBACK="http://localhost:9977/" \
    --env CRYOSTAT_AGENT_BASEURI="https://localhost:8181/" \
    --env CRYOSTAT_AGENT_TRUST_ALL="true" \
    --env CRYOSTAT_AGENT_AUTHORIZATION="Basic $(echo -n user:pass | base64)" \
    --rm -it \
    quay.io/cryostat/cryostat-reports:latest

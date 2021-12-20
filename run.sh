#!/bin/sh

set -x
set -e

if [ -z "${CPUS}" ]; then
    CPUS=1
fi

if [ "${CPUS}" -gt 1 ]; then
    GC="Parallel"
    SINGLETHREAD_JFR_PARSE="false"
else
    GC="Serial"
    SINGLETHREAD_JFR_PARSE="true"
fi

if [ -z "${MEMORY}" ]; then
    MEMORY="512M"
fi

podman run \
    --user 0 \
    --cpus "${CPUS}" \
    --memory "${MEMORY}" \
    --publish 8080:8080 \
    --env JAVA_OPTIONS="-XX:ActiveProcessorCount=${CPUS} -XX:+Use${GC}GC -Dorg.openjdk.jmc.flightrecorder.parser.singlethreaded=${SINGLETHREAD_JFR_PARSE}" \
    --rm -it \
    quay.io/cryostat/cryostat-reports:latest

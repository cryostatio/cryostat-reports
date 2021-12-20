#!/bin/sh

set -x
set -e

if [ -z "${CPUS}" ]; then
    CPUS=1
fi

if [ "${CPUS}" -gt 1 ]; then
    GC="Parallel"
else
    GC="Serial"
fi

if [ -z "${MEMORY}" ]; then
    MEMORY="512M"
fi

podman run \
    --user 0 \
    --cpus "${CPUS}" \
    --memory "${MEMORY}" \
    --publish 8080:8080 \
    --env JAVA_OPTIONS="-XX:ActiveProcessorCount=${CPUS} -XX:+Use${GC}GC" \
    --rm -it \
    quay.io/cryostat/cryostat-reports:latest

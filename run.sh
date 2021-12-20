#!/bin/sh

cpus=2

podman run \
    --user 0 \
    --cpus "${cpus}" \
    --memory 512M \
    --publish 8080:8080 \
    --env JAVA_OPTIONS="-XX:ActiveProcessorCount=${cpus} -XX:+UseParallelGC" \
    --rm -it \
    quay.io/cryostat/cryostat-reports:latest

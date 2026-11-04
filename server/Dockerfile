# The relay image (decision 0081): the output of `./gradlew :server:installDist` on a JRE.
# CI builds and publishes it; build locally with `docker build server` after installDist.
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S -g 10001 relay \
    && adduser -S -D -H -u 10001 -G relay relay \
    && mkdir /data \
    && chown relay:relay /data

COPY build/install/server /opt/relay

ENV MSUITE_RELAY_DATA=/data \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

USER relay
EXPOSE 8080
ENTRYPOINT ["/opt/relay/bin/server"]

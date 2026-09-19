# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && groupadd --gid 10001 scholarmind \
    && useradd --uid 10001 --gid scholarmind --no-create-home scholarmind \
    && mkdir -p /app/data /app/papers /app/workspace \
    && chown -R scholarmind:scholarmind /app
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/scholarmind-1.0-SNAPSHOT.jar ./app.jar
COPY --chown=10001:10001 skills ./skills
COPY LICENSE ./LICENSE
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 9900
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:9900/ > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

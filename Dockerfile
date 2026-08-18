# syntax=docker/dockerfile:1

# ---- Stage 1: build the executable jar ---------------------------------------
# Uses a JDK 21 + Maven image. Dependencies are resolved in a separate layer so that
# code-only changes don't re-download the world on every build (Docker layer caching).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests need a database/Docker; they run in CI, not in the image build.
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: minimal runtime image ------------------------------------------
# Only a JRE + the fat jar. Smaller attack surface and image size than shipping the JDK.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user (defense in depth - a compromised app can't act as root).
RUN groupadd -r flowforge && useradd -r -g flowforge flowforge

COPY --from=build /app/target/flowforge-*.jar app.jar
USER flowforge

EXPOSE 8080

# The 'docker' profile + all secrets/DB settings come from environment variables
# supplied by docker-compose (or the orchestrator).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

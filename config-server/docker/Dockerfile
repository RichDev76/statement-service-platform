# ../config-server/docker/Dockerfile
##
## Multi-stage build (repo-root context)
## - Build stage uses Maven with Eclipse Temurin 21 JDK so release 21 compiles
## - Builds only the config-server module
## - Runtime stage uses Temurin 21 JRE with curl/jq for entrypoint
##

# ---- Build stage (JDK 21) ----
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy entire repository so Maven can resolve parent POMs and sibling modules
COPY . /workspace

# Build only the config-server module (and its dependencies)
RUN mvn -f pom.xml -pl config-server -am -B -DskipTests package

# ---- Runtime stage (JRE 21) ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built jar produced by the build stage
COPY --from=build /workspace/config-server/target/*.jar /app/app.jar

# Install curl & jq so the entrypoint can call Vault API
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl jq ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Copy the entrypoint from the config-server/docker path (repo-root context)
COPY config-server/docker/config-server-entrypoint.sh /opt/config-server/config-server-entrypoint.sh
RUN chmod +x /opt/config-server/config-server-entrypoint.sh

ENTRYPOINT ["/opt/config-server/config-server-entrypoint.sh"]
CMD ["java","-jar","/app/app.jar"]

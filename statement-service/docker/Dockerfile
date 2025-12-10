# =====================================================================
# BUILD STAGE (optional but recommended for clean Docker builds)
# =====================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy root POM and module POMs first to leverage Docker layer cache
COPY pom.xml ./
COPY config-server/pom.xml ./config-server/pom.xml
COPY statement-service/pom.xml ./statement-service/pom.xml

# Pre-fetch dependencies using Maven (cache local Maven repo between builds)
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

# Copy module sources
COPY config-server/src ./config-server/src
COPY statement-service/src ./statement-service/src

# Build only the statement-service module (and any required dependencies)
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests -pl statement-service -am clean package

# =====================================================================
# RUNTIME STAGE
# =====================================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/statement-service/target/*.jar /app/app.jar

# Copy wait + entrypoint scripts
COPY statement-service/docker/wait-for-config.sh /app/wait-for-config.sh
COPY statement-service/docker/entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/wait-for-config.sh \
    && chmod +x /app/entrypoint.sh

# Expose app port
EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]

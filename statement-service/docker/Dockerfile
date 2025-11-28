# ---- Stage 1: Build the application ----
FROM maven:3.9.9-amazoncorretto-24 AS builder

# Set working directory
WORKDIR /app

# Copy Maven configuration and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# ---- Stage 2: Run the application ----
FROM amazoncorretto:24-alpine

# Set working directory
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose Spring Boot's default port
EXPOSE 8080

# Create and switch to a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Run the service
ENTRYPOINT ["java", "-jar", "app.jar"]

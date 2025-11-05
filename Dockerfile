# Multi-stage build for Spring Boot application

# Stage 1: Build
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build application
RUN ./gradlew clean bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r spring && useradd -r -g spring spring

# Copy JAR from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership
RUN chown -R spring:spring /app

USER spring

# Expose port
EXPOSE 8090

# JVM options for 1GB RAM environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
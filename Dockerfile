FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy gradle files from jobsearchcv-backend
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY gradlew ./

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src

# Build application
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

# Install wget for health checks
RUN apk add --no-cache wget

WORKDIR /app

# Create logs directory
RUN mkdir -p logs

# Copy jar from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
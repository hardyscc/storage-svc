FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy the JAR file
COPY target/storage-svc-1.0.0.jar app.jar

# Create storage directories
RUN mkdir -p /app/storage-data /app/bucket-metadata

# Expose port
EXPOSE 9000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:9000/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

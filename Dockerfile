# Based on Bitnami Spark 3.5 image as requested
FROM bitnami/spark:3.5

USER root

# Create directory for the application
WORKDIR /app

# Copy the built JAR file
# Note: Ensure you run 'mvn clean package' before building this image
COPY ./target/spark-performance-insight-*.jar /app/app.jar

# Create directories for data and logs with correct permissions
RUN mkdir -p /data /eventlogs && \
    chown -R 1001:1001 /app /data /eventlogs

# Switch back to non-root user
USER 1001

# Expose the application port
EXPOSE 8081

# Default configuration location (can be overridden by mounting)
# We assume application.yml is either inside the JAR or mounted to /app/config/application.yml
# Spring Boot looks for config in ./config/ or ./ 

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

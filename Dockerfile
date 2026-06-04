# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and download dependencies to cache this layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build package
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Create runtime image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Run as a non-root system user for security compliance
RUN groupadd -r spring && useradd -r -g spring spring

# Create directory for uploads if the app needs it, with correct permissions
RUN mkdir -p /app/upload && chown -R spring:spring /app/upload

USER spring:spring

# Copy the built jar file
COPY --from=build --chown=spring:spring /app/target/rasa-*.jar app.jar

# Expose backend port
EXPOSE 8080

# Start JVM with optimized parameters for container environments
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

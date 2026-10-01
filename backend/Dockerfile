# Stage 1: Build the Spring Boot Application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build JAR package (skipping test suite for fast deployment)
RUN mvn clean package -DskipTests

# Stage 2: Lightweight Production JRE Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the built JAR artifact from build stage
COPY --from=build /app/target/cinebook-backend-1.0.0.jar app.jar

# Cloud providers dynamically set PORT environment variable
ENV PORT=8080
EXPOSE 8080

# Run Spring Boot
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

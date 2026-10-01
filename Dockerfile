# Stage 1: Build the Spring Boot Application from backend/ folder
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy backend files
COPY backend/pom.xml .
COPY backend/src ./src

# Build JAR package
RUN mvn clean package -DskipTests

# Stage 2: Lightweight Production JRE Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/cinebook-backend-1.0.0.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

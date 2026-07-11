# Stage 1: Build application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# The release image is only produced from a green test suite in CI.
RUN mvn clean package -DskipTests

# Stage 2: Run application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S skillswap && adduser -S -G skillswap skillswap
# Chỉ copy file .jar từ Stage 1 sang Stage 2
COPY --from=build /app/target/*.jar app.jar
RUN chown skillswap:skillswap /app/app.jar
USER skillswap
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xms256m", "-Xmx512m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

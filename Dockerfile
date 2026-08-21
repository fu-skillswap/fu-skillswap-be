# syntax=docker/dockerfile:1

# Stage 1: Build application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Tải dependency ở một layer riêng để Docker có thể dùng lại khi source code thay đổi.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Daether.connector.connectTimeout=30000 \
    -Daether.connector.requestTimeout=120000

COPY src ./src
# The release image is only produced from a green test suite in CI.
RUN mvn -B -ntp clean package -DskipTests \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Daether.connector.connectTimeout=30000 \
    -Daether.connector.requestTimeout=120000

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

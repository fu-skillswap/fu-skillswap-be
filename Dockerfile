# Stage 1: Build application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build ra file .jar và bỏ qua test để tăng tốc độ build image
RUN mvn clean package -DskipTests

# Stage 2: Run application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Chỉ copy file .jar từ Stage 1 sang Stage 2
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xms256m", "-Xmx512m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-alpine-3.22 AS build
WORKDIR /app

COPY . .
RUN chmod +x mvnw \
  && ./mvnw -B -q clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine-3.22
WORKDIR /app
COPY --from=build /app/lang-detect-web/target/lang-detect-web-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
# Render sets PORT; Spring Boot maps it via server.port=${PORT:8080}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

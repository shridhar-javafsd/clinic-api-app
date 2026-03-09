FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY target/clinic-api-1.0.0.jar app.jar

EXPOSE 9999

ENTRYPOINT ["java", "-jar", "app.jar"]


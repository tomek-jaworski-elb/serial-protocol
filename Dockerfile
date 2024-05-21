FROM eclipse-temurin:17-jre-alpine
LABEL authors="tomaszja"

WORKDIR /app
COPY target/serial-protocol-1.0.jar app.jar
EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
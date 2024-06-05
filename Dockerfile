FROM eclipse-temurin:17-jre-alpine
LABEL authors="tomaszja"
ARG VERSION=1.1
WORKDIR /app
COPY target/serial-protocol-${VERSION}.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
FROM eclipse-temurin:17-jre-noble
LABEL authors="tomaszja"
ARG VERSION=1.5
WORKDIR /app
COPY target/serial-protocol-${VERSION}.jar app.jar
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
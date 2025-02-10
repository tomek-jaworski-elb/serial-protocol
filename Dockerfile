FROM eclipse-temurin:17-jre-noble
LABEL authors="tomaszja"
ARG VERSION=1.6
WORKDIR /app
COPY /cert/server.p12 cert/server.p12
COPY target/serial-protocol-${VERSION}.jar app.jar
EXPOSE 443
EXPOSE 8081
RUN apt update && apt install -y netcat-openbsd && apt clean
CMD ["java", "-jar", "app.jar"]
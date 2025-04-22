FROM eclipse-temurin:17-jre-noble@sha256:db4dd3e55dd374d87cf00eb784bd44d67900abc87da82080cbae055a22c8ac76
ARG VERSION=1.7
WORKDIR /app
COPY /cert/server.p12 cert/server.p12
COPY target/serial-protocol-${VERSION}.jar app.jar
EXPOSE 443
EXPOSE 8081
RUN apt update && apt install -y netcat-openbsd && apt clean
CMD ["java", "-jar", "app.jar"]


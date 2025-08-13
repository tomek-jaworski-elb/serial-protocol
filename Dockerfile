FROM eclipse-temurin:17-jre-noble
LABEL author="Tomek Jaworski"
ARG VERSION=1.8
LABEL version=$VERSION
WORKDIR /app
COPY /cert/server.p12 cert/server.p12
COPY target/serial-protocol-${VERSION}.jar app.jar
EXPOSE 443 8081
RUN apt update &&  \
    apt install -y --no-install-recommends netcat-openbsd && \
    rm -rf /var/lib/apt/lists/* && \
    rm -rf /var/cache/apk/* &&  \
    useradd -M aisuser && \
    chown -R aisuser:aisuser /app
#USER aisuser
CMD ["java", "-jar", "app.jar"]


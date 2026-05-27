FROM eclipse-temurin:21-jre-noble
LABEL author="Tomek Jaworski"
ARG VERSION=2.0
LABEL version=$VERSION

WORKDIR /app

# Create non-root user with home directory
RUN apt update && \
    apt install -y --no-install-recommends netcat-openbsd && \
    rm -rf /var/lib/apt/lists/* && \
    rm -rf /var/cache/apk/* && \
    useradd -m -s /sbin/nologin aisuser && \
    mkdir -p /app/logs && \
    mkdir -p /app/cert

# Copy application files
COPY /cert/server.p12 cert/server.p12
COPY target/serial-protocol-${VERSION}.jar app.jar

# Set proper permissions
RUN chown -R aisuser:aisuser /app && \
    chmod 750 /app && \
    chmod 750 /app/logs && \
    chmod 750 /app/cert && \
    chmod 644 /app/cert/server.p12 && \
    chmod 644 /app/app.jar

EXPOSE 443 8081

# Switch to non-root user
USER aisuser

CMD ["java", "-jar", "app.jar"]


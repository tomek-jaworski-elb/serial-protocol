FROM eclipse-temurin:25-jre-noble
LABEL author="Tomek Jaworski"
ARG VERSION=3.0
ENV JAVA_TOOL_OPTIONS="-XX:+UseCompactObjectHeaders"
LABEL version=$VERSION

WORKDIR /app

# Create non-root user with home directory
RUN apt update && \
    apt install -y --no-install-recommends netcat-openbsd libcap2-bin && \
    rm -rf /var/lib/apt/lists/* && \
    useradd -m -s /usr/sbin/nologin aisuser && \
    mkdir -p /app/logs && \
    mkdir -p /app/cert

# Copy application files
COPY cert/server.p12 cert/server.p12
COPY target/serial-protocol-${VERSION}.jar app.jar

# Allow the JVM to bind to privileged ports (<1024) without running as root
RUN setcap 'cap_net_bind_service=+ep' "$(readlink -f $(which java))"

# Set proper permissions
RUN chown -R aisuser:aisuser /app && \
    chmod 750 /app && \
    chmod 750 /app/logs && \
    chmod 750 /app/cert && \
    chmod 600 /app/cert/server.p12 && \
    chmod 644 /app/app.jar

EXPOSE 443 8081

# Switch to non-root user
USER aisuser

CMD ["java", "-jar", "app.jar"]
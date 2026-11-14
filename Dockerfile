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

# The container starts as root only to fix what root alone can, then runs the app as aisuser:
#  - the logs volume may hold root-owned files left by an earlier run as root;
#  - serial devices keep the host's owning group (dialout, its GID differs per distro), so
#    aisuser joins whatever group owns them.
# It stays root, with a warning, only when a serial device is still not accessible to aisuser.
# Written inline because the deploy script ships just this Dockerfile, the jar and the cert.
RUN printf '%s\n' \
    '#!/bin/sh' \
    '[ "$(id -u)" = 0 ] || exec "$@"' \
    'chown -R aisuser:aisuser /app/logs' \
    'ttys=$(ls /dev/ttyS* /dev/ttyUSB* /dev/ttyACM* 2>/dev/null)' \
    'groups=$(stat -c %g $ttys 2>/dev/null | sort -u | paste -sd,)' \
    'drop="setpriv --reuid aisuser --regid aisuser --groups ${groups:-$(id -g aisuser)}"' \
    'for t in $ttys; do' \
    '    $drop test -r "$t" -a -w "$t" && continue' \
    '    echo "WARN: aisuser cannot access $t, running the app as root" >&2' \
    '    exec "$@"' \
    'done' \
    'exec $drop "$@"' \
    > /usr/local/bin/entrypoint.sh && \
    chmod 755 /usr/local/bin/entrypoint.sh

EXPOSE 443 8081

ENTRYPOINT ["entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]
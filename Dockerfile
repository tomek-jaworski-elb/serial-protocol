FROM eclipse-temurin:17-jre-alpine
LABEL authors="tomaszja"

WORKDIR /app
COPY serial-protocol-0.0.1-SNAPSHOT.jar .
#EXPOSE 8080

CMD ["java", "-jar", "serial-protocol-0.0.1-SNAPSHOT.jar"]
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY target/clawhub-0.0.1-SNAPSHOT.jar /app/clawhub.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/clawhub.jar"]

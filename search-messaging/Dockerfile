FROM openjdk:11-jre-slim

RUN useradd -s /bin/bash user
USER user
COPY --chown=644 target/search-messaging-*.jar /dina-search-messaging.jar

ENTRYPOINT ["java", "-jar", "/dina-search-cli.jar"]
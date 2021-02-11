FROM openjdk:11-jre-slim

RUN useradd -s /bin/bash user
USER user
COPY --chown=644 target/search-cli-*.jar /dina-search-cli.jar

ENTRYPOINT ["java", "-jar", "/dina-search-cli.jar"]
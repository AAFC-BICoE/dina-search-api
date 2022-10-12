FROM eclipse-temurin:17-jre-jammy

RUN useradd -s /bin/bash user
USER user
COPY --chown=644 target/search-cli-*.jar /dina-search-cli.jar

ENTRYPOINT ["java", "-jar", "/dina-search-cli.jar"]

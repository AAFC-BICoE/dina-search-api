FROM openjdk:11-jre-slim

RUN useradd -s /bin/bash user
USER user
COPY --chown=644 target/search-ws-*.jar /dina-search-ws.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/dina-search-ws.jar"]
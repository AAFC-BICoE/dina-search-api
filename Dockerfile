FROM openjdk:11-jre-slim

RUN useradd -s /bin/bash user
USER user
COPY --chown=644 target/search-cli-*.jar /search-cli.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/search-cli.jar"]

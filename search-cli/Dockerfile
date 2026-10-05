FROM eclipse-temurin:25-jre

RUN useradd -r -u 10001 -s /bin/bash appuser
USER appuser
COPY --chown=appuser:appuser target/search-cli-*.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]

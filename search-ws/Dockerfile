FROM eclipse-temurin:25-jre

RUN useradd -r -u 10001 appuser
USER appuser
COPY --chown=appuser:appuser target/search-ws-*.jar app.jar
EXPOSE 8085

ENTRYPOINT ["java","-jar","app.jar"]

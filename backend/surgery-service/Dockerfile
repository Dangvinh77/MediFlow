# Runtime image for an already packaged, current Surgery executable jar.
# Build context: backend/surgery-service/target (see README).
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY *.jar app.jar
USER 10001
ENV SERVER_PORT=8091
EXPOSE 8091
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

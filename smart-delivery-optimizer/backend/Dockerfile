# ---- Build stage -------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Download dependencies first so they are cached between code changes
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- Runtime stage -----------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as an unprivileged user
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/target/*.jar app.jar
USER app

ENV PORT=8080 \
    APP_DATA_SOURCE=database \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:${PORT}/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

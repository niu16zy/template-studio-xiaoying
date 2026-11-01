FROM eclipse-temurin:21.0.9_10-jdk-jammy AS source
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
COPY scripts scripts
RUN chmod +x scripts/check.sh

FROM source AS checks
CMD ["sh", "scripts/check.sh"]

FROM source AS build
# The checks target runs verify against real MySQL. Image assembly is a separate operation.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:21.0.9_10-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
WORKDIR /app
RUN mkdir -p /app/data && chown app:app /app/data
COPY --from=build --chown=app:app /app/target/material-studio-0.1.0.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC -Djava.awt.headless=true"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=10 CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

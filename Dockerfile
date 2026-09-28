# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the application ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first: this layer stays cached until pom.xml or the wrapper changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Tests are not run here: the integration tests need Docker (Testcontainers),
# which isn't available inside `docker build`. CI runs the full `mvnw verify`.
COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination target/extracted

# ---- Runtime stage: JRE only, non-root, layered for cache-friendly rebuilds ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

# Least to most frequently changing, so a code change only rebuilds the last layer
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER app
EXPOSE 8080

# Size the heap from the container's memory limit, not the host's
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]

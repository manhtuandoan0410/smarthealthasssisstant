# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

COPY --from=builder --chown=10001:0 /workspace/target/smarthhealth-0.0.1-SNAPSHOT.jar app.jar
# OpenShift/Rahti may run containers with an arbitrary UID; keep app files accessible to group 0.
RUN chmod -R g=u /app

USER 10001:0
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder --chown=10001:10001 /workspace/target/smarthhealth-0.0.1-SNAPSHOT.jar app.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY common ./common
COPY nosql-reactive ./nosql-reactive
COPY nosql-blocking/pom.xml ./nosql-blocking/
COPY relational/pom.xml ./relational/
COPY stub-service/pom.xml ./stub-service/
COPY experiment-runner/pom.xml ./experiment-runner/
COPY data-generator/pom.xml ./data-generator/

RUN mvn clean package -DskipTests -pl nosql-reactive -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/nosql-reactive/target/*.jar app.jar
EXPOSE 8082
# Identical JVM flags to the other services (fair comparison) - see nosql-blocking/Dockerfile.
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms128m", "-Xmx512m", "-XX:MaxMetaspaceSize=160m", "-jar", "app.jar"]

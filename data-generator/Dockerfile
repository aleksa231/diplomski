FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY common ./common
COPY data-generator ./data-generator
COPY nosql-blocking/pom.xml ./nosql-blocking/
COPY nosql-reactive/pom.xml ./nosql-reactive/
COPY relational/pom.xml ./relational/
COPY stub-service/pom.xml ./stub-service/
COPY experiment-runner/pom.xml ./experiment-runner/

RUN mvn clean package -DskipTests -pl data-generator -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/data-generator/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

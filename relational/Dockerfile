FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY common ./common
COPY relational ./relational
COPY nosql-blocking/pom.xml ./nosql-blocking/
COPY nosql-reactive/pom.xml ./nosql-reactive/
COPY stub-service/pom.xml ./stub-service/
COPY experiment-runner/pom.xml ./experiment-runner/
COPY data-generator/pom.xml ./data-generator/

RUN mvn clean package -DskipTests -pl relational -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/relational/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms512m", "-Xmx768m", "-jar", "app.jar"]

FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY common ./common
COPY nosql-blocking ./nosql-blocking
COPY nosql-reactive/pom.xml ./nosql-reactive/
COPY relational/pom.xml ./relational/
COPY stub-service/pom.xml ./stub-service/
COPY experiment-runner/pom.xml ./experiment-runner/
COPY data-generator/pom.xml ./data-generator/

RUN mvn clean package -DskipTests -pl nosql-blocking -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/nosql-blocking/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms512m", "-Xmx768m", "-jar", "app.jar"]

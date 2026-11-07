FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY stub-service ./stub-service
COPY common/pom.xml ./common/
COPY nosql-blocking/pom.xml ./nosql-blocking/
COPY nosql-reactive/pom.xml ./nosql-reactive/
COPY relational/pom.xml ./relational/
COPY experiment-runner/pom.xml ./experiment-runner/
COPY data-generator/pom.xml ./data-generator/

RUN mvn clean package -DskipTests -pl stub-service -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/stub-service/target/*.jar app.jar
EXPOSE 8099
# Trivial stub used only to inject latency/faults - a small fixed heap is enough.
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms64m", "-Xmx192m", "-XX:MaxMetaspaceSize=128m", "-jar", "app.jar"]

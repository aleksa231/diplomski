FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY common ./common
COPY experiment-runner ./experiment-runner
COPY nosql-blocking/pom.xml ./nosql-blocking/
COPY nosql-reactive/pom.xml ./nosql-reactive/
COPY relational/pom.xml ./relational/
COPY stub-service/pom.xml ./stub-service/
COPY data-generator/pom.xml ./data-generator/

RUN mvn clean package -DskipTests -pl experiment-runner -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl bash docker-cli && \
    curl -L https://github.com/grafana/k6/releases/download/v0.56.0/k6-v0.56.0-linux-amd64.tar.gz | tar -xz && \
    mv k6-v0.56.0-linux-amd64/k6 /usr/local/bin/k6 && \
    rm -rf k6-v0.56.0-linux-amd64

COPY --from=builder /build/experiment-runner/target/*.jar app.jar
EXPOSE 8090
# The runner also spawns k6 as a child process, so its own JVM heap stays small and leaves the rest of
# the container budget to the load generator.
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms128m", "-Xmx384m", "-XX:MaxMetaspaceSize=160m", "-jar", "app.jar"]

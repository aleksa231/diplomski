.PHONY: up down build test seed scenario-S1 scenario-S2 scenario-S3 scenario-S4 run-all verify-results clean

up:
	docker compose up -d --build

down:
	docker compose down -v

build:
	mvn clean package -DskipTests

test:
	mvn test

seed:
	docker compose run --rm data-generator --size 5000 --seed 42 --orders-ratio 2 --targets mongo,pg-normalized,pg-jsonb

scenario-S1:
	curl -s -X POST http://localhost:8090/api/experiments -H "Content-Type: application/json" -d '{"scenario":"S1","datasetSize":5000,"clients":50,"durationSec":30,"repetitions":3,"targets":["nosql-blocking","relational-normalized","relational-jsonb"]}'

scenario-S2:
	curl -s -X POST http://localhost:8090/api/experiments -H "Content-Type: application/json" -d '{"scenario":"S2","datasetSize":5000,"clients":50,"durationSec":30,"repetitions":3,"targets":["mongo-no-cache","mongo-cache","relational-no-cache","relational-cache"]}'

scenario-S3:
	curl -s -X POST http://localhost:8090/api/experiments -H "Content-Type: application/json" -d '{"scenario":"S3","datasetSize":5000,"clients":100,"durationSec":30,"repetitions":3,"targets":["mongo-majority","postgres"]}'

scenario-S4:
	curl -s -X POST http://localhost:8090/api/experiments -H "Content-Type: application/json" -d '{"scenario":"S4","datasetSize":50000,"clients":20,"durationSec":60,"repetitions":3,"targets":["nosql-blocking","relational"]}'

run-all:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-all-scenarios.ps1

verify-results:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-results.ps1

clean:
	mvn clean

.PHONY: up down build test seed scenario-S1 scenario-S2 scenario-S3 scenario-S4 scenario-S5 scenario-S6 scenario-S7 clean

up:
	docker compose up -d --build

down:
	docker compose down -v

build:
	mvn clean package -DskipTests

test:
	mvn test

seed:
	docker compose run --rm data-generator --size 10000 --seed 42 --orders-ratio 2 --targets mongo,pg-normalized,pg-jsonb

scenario-S1:
	curl -s -X POST http://localhost:8090/api/experiments -H "Content-Type: application/json" -d '{"scenario":"S1","datasetSize":10000,"clients":50,"durationSec":30,"repetitions":3,"targets":["nosql-blocking","relational-normalized","relational-jsonb"]}'

scenario-S2:
	curl -s -X POST http://localhost:8090/api/experiments -H "Content-Type: application/json" -d '{"scenario":"S2","datasetSize":10000,"clients":50,"durationSec":30,"repetitions":3,"targets":["nosql-blocking","relational"]}'

scenario-S3:
	curl -s -X POST http://localhost:8090/api/experiments -H "Content-Type: application/json" -d '{"scenario":"S3","datasetSize":10000,"clients":100,"durationSec":30,"repetitions":3,"targets":["mongo-w1","mongo-majority","postgres"]}'

scenario-S4:
	curl -s -X POST http://localhost:8090/api/experiments -H "Content-Type: application/json" -d '{"scenario":"S4","datasetSize":10000,"clients":20,"durationSec":30,"repetitions":3,"targets":["mongo-indexed","mongo-unindexed","postgres-indexed","postgres-unindexed"]}'

scenario-S5:
	curl -s -X POST http://localhost:8090/api/experiments -H "Content-Type: application/json" -d '{"scenario":"S5","datasetSize":10000,"clients":200,"durationSec":30,"repetitions":3,"targets":["nosql-blocking","nosql-reactive"]}'

scenario-S6:
	curl -s -X POST http://localhost:8090/api/experiments -H "Content-Type: application/json" -d '{"scenario":"S6","datasetSize":10000,"clients":50,"durationSec":30,"repetitions":3,"targets":["failover","redis-down","concurrency"]}'

scenario-S7:
	curl -s -X POST http://localhost:8090/api/experiments -H "Content-Type: application/json" -d '{"scenario":"S7","datasetSize":10000,"clients":20,"durationSec":30,"repetitions":3,"targets":["nosql-blocking","relational"]}'

clean:
	mvn clean

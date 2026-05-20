SERVICE ?= bank-flow-accounts
DASHBOARDS_DIR ?= $(SERVICE)/dashboards
GRAFANA_DASHBOARDS_DIR ?= observability/grafana/dashboards
GRADLEW := cd $(SERVICE) && ./gradlew

.PHONY: up down logs test build api worker baas dashboards-deploy observability-deploy observability-up observability-down observability-logs deploy-observability

up:
	docker compose -f docker-compose.yaml up -d

down:
	docker compose -f docker-compose.yaml down

logs:
	docker compose -f docker-compose.yaml logs -f

test:
	$(GRADLEW) test

build:
	$(GRADLEW) build

api:
	$(GRADLEW) :api:bootRun

worker:
	$(GRADLEW) :worker:bootRun

baas:
	python3 baas-simulator/app.py

dashboards-deploy:
	test -d $(DASHBOARDS_DIR)
	mkdir -p $(GRAFANA_DASHBOARDS_DIR)/$(SERVICE)
	cp $(DASHBOARDS_DIR)/*.json $(GRAFANA_DASHBOARDS_DIR)/$(SERVICE)/

observability-deploy: dashboards-deploy
	docker compose -f observability/docker-compose.yaml up -d

observability-up: observability-deploy

deploy-observability: observability-deploy

observability-down:
	docker compose -f observability/docker-compose.yaml down

observability-logs:
	docker compose -f observability/docker-compose.yaml logs -f

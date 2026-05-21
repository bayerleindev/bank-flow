SERVICE ?= bank-flow-accounts
DASHBOARDS_DIR ?= $(SERVICE)/dashboards
DASHBOARD_SERVICES ?= $(shell find bank-flow-* -maxdepth 1 -type d -name dashboards -print | sed 's|/dashboards||' | sort)
GRAFANA_DASHBOARDS_DIR ?= observability/grafana/dashboards
K8S_MONITORING_NAMESPACE ?= monitoring
DOCKER_NAMESPACE ?= gbcamargo
LEDGER_IMAGE_NAME ?= bank-flow-ledger
IMAGE_TAG ?= dev
GRADLEW := cd $(SERVICE) && ./gradlew

ACCOUNTS_API_PORT ?= 8080
ACCOUNTS_WORKER_PORT ?= 8081
KAFKA_UI_PORT ?= 8082
TRANSFERS_API_PORT ?= 8083
TRANSFERS_WORKER_PORT ?= 8084
LEDGER_PORT ?= 8086
BALANCE_API_PORT ?= 8087
BALANCE_WORKER_PORT ?= 8088
BAAS_PORT ?= 8089
AUTH_PORT ?= 8090
ONBOARDING_API_PORT ?= 8091
AUTH_WORKER_PORT ?= 8092

BAAS_BASE_URL ?= http://localhost:$(BAAS_PORT)
ACCOUNTS_WEBHOOK_URL ?= http://localhost:$(ACCOUNTS_API_PORT)/webhooks/baas/accounts
TRANSFERS_WEBHOOK_URL ?= http://localhost:$(TRANSFERS_API_PORT)/baas/webhooks/transfers
TRANSFERS_WEBHOOK_DELAY_SECONDS ?= 2
TRANSFERS_WEBHOOK_SUCCESS_RATE ?= 0.85
LEDGER_EXTERNAL_SETTLEMENT_ACCOUNT_ID ?= 00000000-0000-0000-0000-000000000001
AUTH_ISSUER_KEY ?= local-dev-issuer-key
AUTH_JWT_ISSUER_URI ?= http://localhost:$(AUTH_PORT)
AUTH_JWKS_URI ?= http://localhost:$(AUTH_PORT)/.well-known/jwks.json

.PHONY: \
	up down logs \
	test build check test-all build-all check-all \
	api worker ledger \
	accounts-api accounts-worker \
	transfers-api transfers-worker \
	balance-api balance-worker \
	auth auth-worker onboarding-api baas \
	ledger-docker-publish \
	dashboards-deploy dashboards-k8s-deploy \
	observability-deploy observability-up observability-down observability-logs deploy-observability

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

check:
	$(GRADLEW) check

test-all:
	cd bank-flow-accounts && ./gradlew test
	cd bank-flow-transfers && ./gradlew test
	cd bank-flow-balance && ./gradlew test
	cd bank-flow-ledger && ./gradlew test
	cd bank-flow-auth && ./gradlew test

build-all:
	cd bank-flow-accounts && ./gradlew build
	cd bank-flow-transfers && ./gradlew build
	cd bank-flow-balance && ./gradlew build
	cd bank-flow-ledger && ./gradlew build
	cd bank-flow-auth && ./gradlew build

check-all:
	cd bank-flow-accounts && ./gradlew check
	cd bank-flow-transfers && ./gradlew check
	cd bank-flow-balance && ./gradlew check
	cd bank-flow-ledger && ./gradlew check
	cd bank-flow-auth && ./gradlew check

api:
	$(GRADLEW) :api:bootRun

worker:
	$(GRADLEW) :worker:bootRun

accounts-api:
	cd bank-flow-accounts && SERVER_PORT=$(ACCOUNTS_API_PORT) BAAS_BASE_URL=$(BAAS_BASE_URL) ./gradlew :api:bootRun

accounts-worker:
	cd bank-flow-accounts && SERVER_PORT=$(ACCOUNTS_WORKER_PORT) BAAS_BASE_URL=$(BAAS_BASE_URL) ./gradlew :worker:bootRun

transfers-api:
	cd bank-flow-transfers && SERVER_PORT=$(TRANSFERS_API_PORT) BAAS_BASE_URL=$(BAAS_BASE_URL) AUTH_JWT_ISSUER_URI=$(AUTH_JWT_ISSUER_URI) AUTH_JWKS_URI=$(AUTH_JWKS_URI) LEDGER_EXTERNAL_SETTLEMENT_ACCOUNT_ID=$(LEDGER_EXTERNAL_SETTLEMENT_ACCOUNT_ID) ./gradlew :api:bootRun

transfers-worker:
	cd bank-flow-transfers && SERVER_PORT=$(TRANSFERS_WORKER_PORT) BAAS_BASE_URL=$(BAAS_BASE_URL) ./gradlew :worker:bootRun

balance-api:
	cd bank-flow-balance && SERVER_PORT=$(BALANCE_API_PORT) ./gradlew :api:bootRun

balance-worker:
	cd bank-flow-balance && SERVER_PORT=$(BALANCE_WORKER_PORT) ./gradlew :worker:bootRun

ledger:
	cd bank-flow-ledger && SERVER_PORT=$(LEDGER_PORT) LEDGER_EXTERNAL_SETTLEMENT_ACCOUNT_ID=$(LEDGER_EXTERNAL_SETTLEMENT_ACCOUNT_ID) ./gradlew bootRun

ledger-docker-publish:
	docker build -t $(LEDGER_IMAGE_NAME):$(IMAGE_TAG) bank-flow-ledger
	docker tag $(LEDGER_IMAGE_NAME):$(IMAGE_TAG) $(DOCKER_NAMESPACE)/$(LEDGER_IMAGE_NAME):$(IMAGE_TAG)
	docker push $(DOCKER_NAMESPACE)/$(LEDGER_IMAGE_NAME):$(IMAGE_TAG)

auth:
	cd bank-flow-auth && SERVER_PORT=$(AUTH_PORT) AUTH_ISSUER_KEY=$(AUTH_ISSUER_KEY) AUTH_JWT_ISSUER=$(AUTH_JWT_ISSUER_URI) ./gradlew :api:bootRun

auth-worker:
	cd bank-flow-auth && SERVER_PORT=$(AUTH_WORKER_PORT) ./gradlew :worker:bootRun

onboarding-api:
	cd bank-flow-onboarding && SERVER_PORT=$(ONBOARDING_API_PORT) AUTH_BASE_URL=http://localhost:$(AUTH_PORT) AUTH_ISSUER_KEY=$(AUTH_ISSUER_KEY) ./gradlew :api:bootRun

baas:
	BAAS_PORT=$(BAAS_PORT) ACCOUNTS_WEBHOOK_URL=$(ACCOUNTS_WEBHOOK_URL) TRANSFERS_WEBHOOK_URL=$(TRANSFERS_WEBHOOK_URL) TRANSFERS_WEBHOOK_DELAY_SECONDS=$(TRANSFERS_WEBHOOK_DELAY_SECONDS) TRANSFERS_WEBHOOK_SUCCESS_RATE=$(TRANSFERS_WEBHOOK_SUCCESS_RATE) python3 baas-simulator/app.py

dashboards-deploy:
	@for service in $(DASHBOARD_SERVICES); do \
		test -d $$service/dashboards; \
		mkdir -p $(GRAFANA_DASHBOARDS_DIR)/$$service; \
		cp $$service/dashboards/*.json $(GRAFANA_DASHBOARDS_DIR)/$$service/; \
		echo "deployed dashboards for $$service"; \
	done

dashboards-k8s-deploy:
	@for service in bank-flow-*/dashboards; do \
		name="$$(dirname "$$service")"; \
		kubectl create configmap "$${name}-dashboards" \
			--namespace $(K8S_MONITORING_NAMESPACE) \
			--from-file="$$service" \
			--dry-run=client -o yaml \
			| kubectl label --local -f - grafana_dashboard=1 -o yaml \
			| kubectl apply -f -; \
	done

observability-deploy: dashboards-deploy
	docker compose -f observability/docker-compose.yaml up -d

observability-up: observability-deploy

deploy-observability: observability-deploy

observability-down:
	docker compose -f observability/docker-compose.yaml down

observability-logs:
	docker compose -f observability/docker-compose.yaml logs -f

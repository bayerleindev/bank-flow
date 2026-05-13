SHELL := /bin/bash

COMPOSE := docker compose
COMPOSE_INFRA := -f docker-compose.yaml
COMPOSE_ALL := -f docker-compose.yaml -f docker-compose.apps.yaml

K8S_NAMESPACE ?= bank-flow
K8S_RENDER_DIR ?= /tmp/bank-flow-k8s
KUBECTL ?= kubectl
HELM ?= helm
MINIKUBE ?= minikube

ACCOUNTS_IMAGE ?= bank-flow-accounts:local
OUTBOXER_IMAGE ?= bank-flow-outboxer:local
LEDGER_IMAGE ?= bank-flow-ledger:local
BALANCE_API_IMAGE ?= bank-flow-balance-api:local
BALANCE_WORKER_IMAGE ?= bank-flow-balance-worker:local
TRANSFER_API_IMAGE ?= bank-flow-transfer-api:local
TRANSFER_WORKER_IMAGE ?= bank-flow-transfer-worker:local
YIELD_IMAGE ?= bank-flow-yield:local

.PHONY: help
help:
	@printf "Bank Flow targets\n\n"
	@printf "Docker / Compose:\n"
	@printf "  make docker-build          Build all local application images\n"
	@printf "  make compose-up            Start infra + all applications\n"
	@printf "  make compose-up-infra      Start only Postgres, Kafka, immudb and Kafka UI\n"
	@printf "  make compose-up-apps       Start application containers using existing images\n"
	@printf "  make compose-down          Stop all compose services\n"
	@printf "  make compose-logs          Follow all compose logs\n"
	@printf "  make compose-ps            Show compose service status\n"
	@printf "\nKubernetes using kubectl:\n"
	@printf "  make k8s-render            Render Helm charts to $(K8S_RENDER_DIR)\n"
	@printf "  make k8s-apply             Apply rendered manifests with kubectl\n"
	@printf "  make k8s-deploy            Build images, load to minikube, render and apply\n"
	@printf "  make k8s-status            Show pods, services, hpa, pdb and scaledobjects\n"
	@printf "  make k8s-delete            Delete rendered manifests\n"
	@printf "\nQuality:\n"
	@printf "  make test                  Run all Gradle tests\n"
	@printf "  make helm-lint             Lint all Helm charts\n"
	@printf "\nLoad tests:\n"
	@printf "  make k6-smoke              Run a short low-rate k6 E2E smoke test\n"
	@printf "  make k6-heavy              Run the heavy k6 E2E load test\n"

.PHONY: docker-build
docker-build: docker-build-accounts docker-build-outboxer docker-build-yield docker-build-balance-api docker-build-balance-worker docker-build-ledger docker-build-transfer-api docker-build-transfer-worker

.PHONY: docker-build-accounts
docker-build-accounts:
	cd bank-flow-accounts && ./gradlew bootBuildImage --imageName=$(ACCOUNTS_IMAGE)

.PHONY: docker-build-outboxer
docker-build-outboxer:
	cd bank-flow-outboxer && ./gradlew bootBuildImage --imageName=$(OUTBOXER_IMAGE)

.PHONY: docker-build-yield
docker-build-yield:
	cd bank-flow-yield && ./gradlew bootBuildImage --imageName=$(YIELD_IMAGE)

.PHONY: docker-build-ledger
docker-build-ledger:
	cd bank-flow-ledger && ./gradlew bootBuildImage --imageName=$(LEDGER_IMAGE)

.PHONY: docker-build-balance-api
docker-build-balance-api:
	cd bank-flow-balance && ./gradlew :api:bootBuildImage --imageName=$(BALANCE_API_IMAGE)

.PHONY: docker-build-balance-worker
docker-build-balance-worker:
	cd bank-flow-balance && ./gradlew :worker:bootBuildImage --imageName=$(BALANCE_WORKER_IMAGE)

.PHONY: docker-build-transfer-api
docker-build-transfer-api:
	cd bank-flow-transfer && ./gradlew :api:bootBuildImage --imageName=$(TRANSFER_API_IMAGE)

.PHONY: docker-build-transfer-worker
docker-build-transfer-worker:
	cd bank-flow-transfer && ./gradlew :worker:bootBuildImage --imageName=$(TRANSFER_WORKER_IMAGE)

.PHONY: compose-up
compose-up: docker-build
	$(COMPOSE) $(COMPOSE_ALL) up -d

.PHONY: compose-up-infra
compose-up-infra:
	$(COMPOSE) $(COMPOSE_INFRA) up -d db kafka kafka-init immudb kafka-ui

.PHONY: compose-up-apps
compose-up-apps:
	$(COMPOSE) $(COMPOSE_ALL) up -d bank-flow-outboxer bank-flow-accounts bank-flow-yield bank-flow-ledger bank-flow-balance-api bank-flow-balance-worker bank-flow-transfer-api bank-flow-transfer-worker

.PHONY: compose-down
compose-down:
	$(COMPOSE) $(COMPOSE_ALL) down

.PHONY: compose-down-volumes
compose-down-volumes:
	$(COMPOSE) $(COMPOSE_ALL) down -v

.PHONY: compose-logs
compose-logs:
	$(COMPOSE) $(COMPOSE_ALL) logs -f

.PHONY: compose-ps
compose-ps:
	$(COMPOSE) $(COMPOSE_ALL) ps

.PHONY: test
test: test-accounts test-outboxer test-yield test-ledger test-balance test-transfer

.PHONY: test-accounts
test-accounts:
	cd bank-flow-accounts && ./gradlew test

.PHONY: test-outboxer
test-outboxer:
	cd bank-flow-outboxer && ./gradlew test

.PHONY: test-yield
test-yield:
	cd bank-flow-yield && ./gradlew test

.PHONY: test-ledger
test-ledger:
	cd bank-flow-ledger && ./gradlew test

.PHONY: test-balance
test-balance:
	cd bank-flow-balance && ./gradlew test

.PHONY: test-transfer
test-transfer:
	cd bank-flow-transfer && ./gradlew test

.PHONY: minikube-load-images
minikube-load-images: docker-build
	$(MINIKUBE) image load $(ACCOUNTS_IMAGE)
	$(MINIKUBE) image load $(OUTBOXER_IMAGE)
	$(MINIKUBE) image load $(YIELD_IMAGE)
	$(MINIKUBE) image load $(BALANCE_API_IMAGE)
	$(MINIKUBE) image load $(BALANCE_WORKER_IMAGE)
	$(MINIKUBE) image load $(LEDGER_IMAGE)
	$(MINIKUBE) image load $(TRANSFER_API_IMAGE)
	$(MINIKUBE) image load $(TRANSFER_WORKER_IMAGE)

.PHONY: helm-lint
helm-lint:
	$(HELM) lint bank-flow-accounts/k8s
	$(HELM) lint bank-flow-outboxer/k8s
	$(HELM) lint bank-flow-yield/k8s
	$(HELM) lint bank-flow-balance/k8s
	$(HELM) lint bank-flow-ledger/k8s
	$(HELM) lint bank-flow-transfer/k8s

.PHONY: k6-smoke
k6-smoke:
	DURATION=1m SEED_ACCOUNTS=5 ACCOUNT_RATE=1 EXTERNAL_RATE=2 INTERNAL_RATE=1 BALANCE_READ_RATE=5 k6 run scripts/k6/heavy-e2e.js

.PHONY: k6-heavy
k6-heavy:
	k6 run scripts/k6/heavy-e2e.js

.PHONY: k8s-namespace
k8s-namespace:
	$(KUBECTL) create namespace $(K8S_NAMESPACE) --dry-run=client -o yaml | $(KUBECTL) apply -f -

.PHONY: k8s-render
k8s-render:
	mkdir -p $(K8S_RENDER_DIR)
	$(HELM) template bank-flow-accounts bank-flow-accounts/k8s --namespace $(K8S_NAMESPACE) > $(K8S_RENDER_DIR)/bank-flow-accounts.yaml
	$(HELM) template bank-flow-outboxer bank-flow-outboxer/k8s --namespace $(K8S_NAMESPACE) > $(K8S_RENDER_DIR)/bank-flow-outboxer.yaml
	$(HELM) template bank-flow-yield bank-flow-yield/k8s --namespace $(K8S_NAMESPACE) > $(K8S_RENDER_DIR)/bank-flow-yield.yaml
	$(HELM) template bank-flow-balance bank-flow-balance/k8s --namespace $(K8S_NAMESPACE) > $(K8S_RENDER_DIR)/bank-flow-balance.yaml
	$(HELM) template bank-flow-ledger bank-flow-ledger/k8s --namespace $(K8S_NAMESPACE) > $(K8S_RENDER_DIR)/bank-flow-ledger.yaml
	$(HELM) template bank-flow-transfer bank-flow-transfer/k8s --namespace $(K8S_NAMESPACE) > $(K8S_RENDER_DIR)/bank-flow-transfer.yaml

.PHONY: k8s-apply
k8s-apply: k8s-namespace k8s-render
	$(KUBECTL) apply -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-accounts.yaml
	$(KUBECTL) apply -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-outboxer.yaml
	$(KUBECTL) apply -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-yield.yaml
	$(KUBECTL) apply -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-balance.yaml
	$(KUBECTL) apply -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-ledger.yaml
	$(KUBECTL) apply -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-transfer.yaml

.PHONY: k8s-deploy
k8s-deploy: minikube-load-images k8s-apply

.PHONY: k8s-status
k8s-status:
	$(KUBECTL) get pods,svc,hpa,pdb,scaledobject -n $(K8S_NAMESPACE)

.PHONY: k8s-rollout-status
k8s-rollout-status:
	$(KUBECTL) rollout status deployment/bank-flow-accounts -n $(K8S_NAMESPACE)
	$(KUBECTL) rollout status deployment/bank-flow-outboxer -n $(K8S_NAMESPACE)
	$(KUBECTL) rollout status deployment/bank-flow-yield -n $(K8S_NAMESPACE)
	$(KUBECTL) rollout status deployment/bank-flow-balance-api -n $(K8S_NAMESPACE)
	$(KUBECTL) rollout status deployment/bank-flow-balance-worker -n $(K8S_NAMESPACE)
	$(KUBECTL) rollout status statefulset/bank-flow-ledger -n $(K8S_NAMESPACE)
	$(KUBECTL) rollout status deployment/bank-flow-transfer-api -n $(K8S_NAMESPACE)
	$(KUBECTL) rollout status deployment/bank-flow-transfer-worker -n $(K8S_NAMESPACE)

.PHONY: k8s-delete
k8s-delete: k8s-render
	$(KUBECTL) delete -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-transfer.yaml --ignore-not-found=true
	$(KUBECTL) delete -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-ledger.yaml --ignore-not-found=true
	$(KUBECTL) delete -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-balance.yaml --ignore-not-found=true
	$(KUBECTL) delete -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-yield.yaml --ignore-not-found=true
	$(KUBECTL) delete -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-accounts.yaml --ignore-not-found=true
	$(KUBECTL) delete -n $(K8S_NAMESPACE) -f $(K8S_RENDER_DIR)/bank-flow-outboxer.yaml --ignore-not-found=true

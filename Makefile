up:
	docker compose up db kafka kafka-init immudb kafka-ui -d
	
	cd bank-flow-accounts; ./gradlew bootBuildImage --imageName=bank-flow-accounts:local
	minikube image load bank-flow-accounts:local

	cd bank-flow-balance; ./gradlew bootBuildImage --imageName=bank-flow-balance:local
	minikube image load bank-flow-balance:local

	cd bank-flow-ledger; ./gradlew bootBuildImage --imageName=bank-flow-ledger:local
	minikube image load bank-flow-ledger:local

	cd bank-flow-transfer; ./gradlew :api:bootBuildImage --imageName=bank-flow-transfer-api:local
	minikube image load bank-flow-transfer-api:local

	cd bank-flow-transfer; ./gradlew :worker:bootBuildImage --imageName=bank-flow-transfer-worker:local
	minikube image load bank-flow-transfer-worker:local

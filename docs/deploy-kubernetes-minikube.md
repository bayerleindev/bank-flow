# Deploy no Kubernetes com Minikube

Este guia descreve o passo a passo para subir o Bank Flow no Minikube usando Helm, com observabilidade via kube-prometheus-stack, Blackbox Exporter, Loki, Grafana Alloy e Tempo.

## 1. Arquivos importantes

Charts Helm dos servicos:

```text
bank-flow-accounts/k8s/
bank-flow-balance/k8s/
bank-flow-ledger/k8s/
bank-flow-transfer/k8s/
```

Arquivos principais de cada chart:

```text
Chart.yaml
values.yaml
templates/configmap.yaml
templates/secret.yaml
templates/service.yaml
templates/deployment.yaml
templates/servicemonitor.yaml
templates/probe.yaml
```

Observacao: o `bank-flow-ledger` usa `StatefulSet`, mesmo que o arquivo ainda se chame `deployment.yaml`.

Configuracoes da stack de observabilidade:

```text
observability/k8s/kube-prometheus-stack-values.yaml
observability/k8s/loki-values.yaml
observability/k8s/alloy-values.yaml
observability/k8s/tempo-values.yaml
```

Dashboards Grafana:

```text
observability/grafana/dashboards/
```

Infra local externa ao Minikube:

```text
docker-compose.yaml
```

Documentacao funcional usada para validar os fluxos depois do deploy:

```text
docs/fluxos-regras-validacoes.md
```

## 2. Dependencias fora do Minikube

Neste setup, Postgres, Kafka e immudb rodam fora do Minikube via Docker Compose. Os pods acessam esses servicos pelo host:

```text
host.minikube.internal
```

Defaults nos charts:

```text
Postgres: host.minikube.internal:5432
Kafka:    host.docker.internal:9094
immudb:   host.docker.internal:3322
```

Suba as dependencias:

```bash
docker compose up -d db kafka kafka-init kafka-ui immudb
```

Valide o Kafka:

```bash
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

## 3. Criar Minikube com recursos suficientes

Para rodar observabilidade e os servicos Java, use pelo menos 8 GB:

```bash
minikube start \
  --memory=8192 \
  --cpus=4 \
  --disk-size=30g
```

Se a maquina permitir, prefira:

```bash
minikube start \
  --memory=12288 \
  --cpus=4 \
  --disk-size=40g
```

Habilite metrics-server para diagnosticar consumo:

```bash
minikube addons enable metrics-server
```

## 4. Build e carga das imagens locais

O Minikube nao enxerga automaticamente as imagens criadas no Docker local. Depois de criar cada imagem, carregue no cluster.

```bash
cd bank-flow-accounts
./gradlew bootBuildImage --imageName=bank-flow-accounts:local
minikube image load bank-flow-accounts:local

cd ../bank-flow-balance
./gradlew :api:bootBuildImage --imageName=bank-flow-balance-api:local
minikube image load bank-flow-balance-api:local

./gradlew :worker:bootBuildImage --imageName=bank-flow-balance-worker:local
minikube image load bank-flow-balance-worker:local

cd ../bank-flow-ledger
./gradlew bootBuildImage --imageName=bank-flow-ledger:local
minikube image load bank-flow-ledger:local

cd ../bank-flow-transfer
./gradlew bootBuildImage --imageName=bank-flow-transfer:local
minikube image load bank-flow-transfer:local

cd ..
```

Os charts usam por padrao:

```yaml
image:
  repository: bank-flow-<servico>
  tag: local
  pullPolicy: IfNotPresent
```

## 5. Repos Helm e namespace

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

kubectl create namespace monitoring
```

Se o namespace ja existir, ignore o erro.

## 6. Subir observabilidade

A forma recomendada e usar o script versionado. Ele instala ou atualiza Prometheus, Grafana, Blackbox Exporter, Loki, Grafana Alloy e Tempo no namespace `monitoring`:

```bash
scripts/k8s/deploy_observability.sh
```

Para usar outro namespace:

```bash
OBSERVABILITY_NAMESPACE=observability scripts/k8s/deploy_observability.sh
```

O script aplica estes releases Helm:

```bash
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  -f observability/k8s/kube-prometheus-stack-values.yaml
```

Blackbox Exporter:

```bash
helm upgrade --install prometheus-blackbox-exporter prometheus-community/prometheus-blackbox-exporter \
  --namespace monitoring
```

Loki:

```bash
helm upgrade --install loki grafana/loki \
  --namespace monitoring \
  -f observability/k8s/loki-values.yaml
```

Grafana Alloy:

```bash
helm upgrade --install alloy grafana/alloy \
  --namespace monitoring \
  -f observability/k8s/alloy-values.yaml
```

Tempo:

```bash
helm upgrade --install tempo grafana/tempo \
  --namespace monitoring \
  -f observability/k8s/tempo-values.yaml
```

A validacao recomendada tambem esta versionada:

```bash
scripts/k8s/validate_observability.sh
```

Ela verifica pods e services, confere os datasources Loki/Tempo provisionados no Grafana e consulta os endpoints internos:

- `http://loki-gateway.monitoring.svc.cluster.local/loki/api/v1/labels`
- `http://tempo.monitoring.svc.cluster.local:3200/api/v2/search/tags`
- `Grafana -> datasource proxy -> Loki`

No Grafana Explore, selecione o datasource `Loki` e use uma destas queries:

```logql
{namespace="bank-flow"}
{stack="bank-flow"}
{application="bank-flow-transfer-api"}
{trace_id!="", namespace="bank-flow"}
{transaction_id="<transfer_id>"}
{transfer_id="<transfer_id>"}
{account_id="<digital_account_id>"}
```

Se a tela vier vazia, confira a janela de tempo. Em Minikube, depois de reinstalar pods ou rodar testes, use primeiro `Last 1 hour` ou `Last 6 hours`.

## 7. Subir os servicos

```bash
helm upgrade --install bank-flow-accounts bank-flow-accounts/k8s
helm upgrade --install bank-flow-balance bank-flow-balance/k8s
helm upgrade --install bank-flow-ledger bank-flow-ledger/k8s
helm upgrade --install bank-flow-transfer bank-flow-transfer/k8s
```

Valide:

```bash
kubectl get pods
kubectl get svc
kubectl get servicemonitor
kubectl get probe
```

## 8. Configuracoes importantes dos charts

Todos os servicos enviam traces para Tempo:

```yaml
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT: "http://tempo.monitoring.svc.cluster.local:4318/v1/traces"
```

Todos os servicos escrevem logs em stdout no Kubernetes:

```yaml
LOG_FILE: ""
```

O Alloy coleta esses logs dos pods, extrai `traceId`/`spanId` dos logs JSON e envia para Loki. O Grafana fica com dois datasources adicionais:

- `Loki`, apontando para `http://loki-gateway.monitoring.svc.cluster.local`;
- `Tempo`, apontando para `http://tempo.monitoring.svc.cluster.local:3200`.

Esses datasources permitem navegar de um log no Loki para o trace no Tempo quando o log possui `traceId`.

Para suporte e investigacao de negocio, os servicos tambem colocam identificadores funcionais nos logs e spans:

- `transaction_id`: no fluxo de transferencia, equivale ao `transfer_id`;
- `transfer_id`: id da transferencia;
- `account_id`: chave de conta usada no evento Kafka, normalmente o `digital_account_id`;
- `source_digital_account_id` e `destination_digital_account_id`;
- `external_id` e `entry_id` para eventos de ledger.

Todos usam labels compatíveis com os dashboards:

```yaml
prometheus-job: "bank-flow-services"
service: bank-flow-<servico>
stack: bank-flow
```

Todos criam `ServiceMonitor` para:

```text
/actuator/prometheus
```

Todos criam `Probe` para:

```text
/actuator/health
```

O `bank-flow-ledger` usa `StatefulSet` e deriva o worker id do ordinal do pod:

```text
bank-flow-ledger-0 -> worker 0
bank-flow-ledger-1 -> worker 1
```

## 9. Exemplos dos manifests renderizados

Os YAMLs abaixo sao exemplos representativos do que os charts Helm renderizam. Para ver o manifesto completo de um servico:

```bash
helm template bank-flow-transfer bank-flow-transfer/k8s
```

### 9.1. ConfigMap

Exemplo do `bank-flow-transfer`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: bank-flow-transfer-config
  labels:
    app.kubernetes.io/name: bank-flow-transfer
    app.kubernetes.io/part-of: bank-flow
data:
  SERVER_PORT: "8083"
  POSTGRES_URL: "jdbc:postgresql://host.minikube.internal:5432/bank_flow?currentSchema=transfer,public"
  KAFKA_BOOTSTRAP_SERVERS: "host.docker.internal:9094"
  ACCOUNTS_BASE_URL: "http://bank-flow-accounts:8084"
  BALANCE_BASE_URL: "http://bank-flow-balance-api:8082"
  OTEL_EXPORTER_OTLP_TRACES_ENDPOINT: "http://tempo.monitoring.svc.cluster.local:4318/v1/traces"
  LOG_FILE: ""
  BPL_JVM_THREAD_COUNT: "50"
```

Pontos importantes:

- `host.minikube.internal` e usado para dependencias fora do cluster.
- `bank-flow-accounts` e `bank-flow-balance-api` sao DNS internos de Services no mesmo namespace.
- `LOG_FILE: ""` faz o app escrever em stdout/stderr.

### 9.2. Secret

Exemplo de credenciais Postgres:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: bank-flow-transfer-secret
  labels:
    app.kubernetes.io/name: bank-flow-transfer
    app.kubernetes.io/part-of: bank-flow
type: Opaque
stringData:
  POSTGRES_USER: "myuser"
  POSTGRES_PASSWORD: "mysecretpassword"
```

Em ambiente real, evite versionar segredo em texto claro. Para a simulacao local, isso simplifica o setup.

### 9.3. Service

Exemplo do `bank-flow-transfer`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: bank-flow-transfer
  labels:
    app.kubernetes.io/name: bank-flow-transfer
    app.kubernetes.io/part-of: bank-flow
    prometheus-job: "bank-flow-services"
    service: bank-flow-transfer
    stack: bank-flow
spec:
  type: ClusterIP
  selector:
    app.kubernetes.io/name: bank-flow-transfer
  ports:
    - name: http
      port: 8083
      targetPort: http
```

Esse Service permite chamadas internas como:

```text
http://bank-flow-transfer:8083
http://bank-flow-transfer.default.svc.cluster.local:8083
```

Os labels `prometheus-job`, `service` e `stack` sao preservados pelo `ServiceMonitor` para manter compatibilidade com os dashboards.

### 9.4. Deployment

Exemplo do `bank-flow-transfer`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bank-flow-transfer
  labels:
    app.kubernetes.io/name: bank-flow-transfer
    app.kubernetes.io/part-of: bank-flow
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: bank-flow-transfer
  template:
    metadata:
      labels:
        app.kubernetes.io/name: bank-flow-transfer
        app.kubernetes.io/part-of: bank-flow
    spec:
      containers:
        - name: bank-flow-transfer
          image: "bank-flow-transfer:local"
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8083
          envFrom:
            - configMapRef:
                name: bank-flow-transfer-config
            - secretRef:
                name: bank-flow-transfer-secret
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: http
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 768Mi
```

`readinessProbe` controla quando o pod pode receber trafego. `livenessProbe` controla quando o Kubernetes deve reiniciar o container.

### 9.5. StatefulSet do ledger

O `ledger` usa `StatefulSet` para ter nome de pod estavel:

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: bank-flow-ledger
spec:
  serviceName: bank-flow-ledger
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: bank-flow-ledger
  template:
    metadata:
      labels:
        app.kubernetes.io/name: bank-flow-ledger
        app.kubernetes.io/part-of: bank-flow
    spec:
      containers:
        - name: bank-flow-ledger
          image: "bank-flow-ledger:local"
          env:
            - name: ID_GENERATOR_WORKER_ID
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
```

O app recebe `bank-flow-ledger-0`, extrai o ordinal `0` e usa como worker id.

### 9.6. ServiceMonitor

Exemplo para metricas Prometheus:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: bank-flow-transfer
  labels:
    app.kubernetes.io/name: bank-flow-transfer
    app.kubernetes.io/part-of: bank-flow
    release: "monitoring"
spec:
  jobLabel: prometheus-job
  targetLabels:
    - service
    - stack
  selector:
    matchLabels:
      app.kubernetes.io/name: bank-flow-transfer
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 15s
      scrapeTimeout: 10s
```

Esse manifesto depende das CRDs do `kube-prometheus-stack`. Se o stack nao estiver instalado, o Kubernetes nao reconhece `ServiceMonitor`.

### 9.7. Probe

Exemplo para Blackbox Exporter:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: Probe
metadata:
  name: bank-flow-transfer-health
  labels:
    app.kubernetes.io/name: bank-flow-transfer
    app.kubernetes.io/part-of: bank-flow
    release: "monitoring"
spec:
  jobName: bank-flow-health
  module: http_2xx
  prober:
    url: prometheus-blackbox-exporter.monitoring.svc:9115
  targets:
    staticConfig:
      static:
        - http://bank-flow-transfer.default.svc:8083/actuator/health
      labels:
        service: bank-flow-transfer
        stack: bank-flow
```

Esse manifesto gera series como:

```promql
probe_success{job="bank-flow-health", service="bank-flow-transfer"}
probe_http_status_code{job="bank-flow-health", service="bank-flow-transfer"}
```

### 9.8. Values de observabilidade

Exemplo reduzido do `kube-prometheus-stack-values.yaml`:

```yaml
prometheus:
  prometheusSpec:
    enableRemoteWriteReceiver: true

grafana:
  sidecar:
    dashboards:
      enabled: true
      label: grafana_dashboard
      labelValue: "1"
      searchNamespace: ALL
  additionalDataSources:
    - name: Loki
      uid: Loki
      type: loki
      access: proxy
      url: http://loki-gateway.monitoring.svc.cluster.local
    - name: Tempo
      uid: tempo
      type: tempo
      access: proxy
      url: http://tempo.monitoring.svc.cluster.local:3200
```

Nao recriamos o datasource Prometheus aqui, porque o `kube-prometheus-stack` ja cria Prometheus e Alertmanager.

### 9.9. Values do Alloy

Exemplo reduzido:

```yaml
controller:
  type: daemonset

alloy:
  clustering:
    enabled: true
  configMap:
    create: true
    content: |-
      discovery.kubernetes "pods" {
        role = "pod"
      }

      loki.source.kubernetes "pods" {
        targets    = discovery.relabel.pods.output
        forward_to = [loki.process.bank_flow.receiver]
      }

      loki.process "bank_flow" {
        stage.cri {}
        stage.json {
          expressions = {
            level    = "level",
            logger   = "logger_name",
            message  = "message",
            trace_id = "traceId",
            span_id  = "spanId",
          }
        }
        forward_to = [loki.write.default.receiver]
      }

      loki.write "default" {
        endpoint {
          url = "http://loki-gateway.monitoring.svc.cluster.local/loki/api/v1/push"
        }
      }
```

O Alloy roda em todos os nos como DaemonSet e envia logs dos pods para Loki.

## 10. Importar dashboards no Grafana

```bash
kubectl create configmap bank-flow-dashboards \
  -n monitoring \
  --from-file=observability/grafana/dashboards \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl label configmap bank-flow-dashboards \
  -n monitoring \
  grafana_dashboard=1 \
  --overwrite
```

Acesse o Grafana:

```bash
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
```

URL:

```text
http://localhost:3000
```

## 11. Chamar as APIs

Use `port-forward` para expor localmente:

```bash
kubectl port-forward svc/bank-flow-accounts 8084:8084
kubectl port-forward svc/bank-flow-balance-api 8082:8082
kubectl port-forward svc/bank-flow-transfer 8083:8083
kubectl port-forward svc/bank-flow-ledger 8085:8085
```

Health do transfer:

```bash
curl http://localhost:8083/actuator/health
```

Criar conta:

```bash
curl -X POST http://localhost:8084/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "owner_id": "11111111-1111-1111-1111-111111111111",
    "branch": "0001",
    "currency": "BRL"
  }'
```

Criar transferencia:

```bash
curl -X POST http://localhost:8083/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: transfer-001" \
  -d '{
    "source_digital_account_id": "UUID_DA_CONTA_ORIGEM",
    "destination_digital_account_id": "UUID_DA_CONTA_DESTINO",
    "amount_minor": 1000,
    "currency": "BRL"
  }'
```

Transferencia inbound externa:

```bash
curl -X POST http://localhost:8083/webhooks/external-institutions/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "external_id": "external-in-001",
    "destination_digital_account_id": "UUID_DA_CONTA_DESTINO",
    "amount_minor": 2500,
    "currency": "BRL",
    "source_institution": "external-bank"
}'
```

## 12. Validar metricas

No Prometheus ou Grafana Explore:

```promql
up{job="bank-flow-services"}
up{job="bank-flow-services", service="bank-flow-transfer"}
probe_success{job="bank-flow-health"}
probe_success{job="bank-flow-health", service="bank-flow-transfer"}
process_uptime_seconds{job="bank-flow-services"}
```

Metricas de negocio:

```promql
transfer_end_to_end_latency_seconds_count
transfers_in_status
outbox_pending_events
ledger_posting_created_total
balance_projection_lag_seconds
```

## 13. Validar logs no Loki

No Grafana Explore, datasource `Loki`:

```logql
{stack="bank-flow"}
{application="bank-flow-transfer"}
{level="ERROR"}
```

Se nao aparecer log:

```bash
kubectl logs -n monitoring daemonset/alloy
kubectl get pods -n monitoring | grep alloy
kubectl get svc -n monitoring | grep loki
```

## 14. Validar traces no Tempo

Confira se o servico esta apontando para o Tempo:

```bash
kubectl exec -it deploy/bank-flow-transfer -- printenv | grep OTEL
```

Esperado:

```text
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://tempo.monitoring.svc.cluster.local:4318/v1/traces
```

No Grafana Explore, datasource `Tempo`, procure pelos servicos:

```text
bank-flow-accounts
bank-flow-balance
bank-flow-ledger
bank-flow-transfer
```

## 15. Troubleshooting rapido

Pods:

```bash
kubectl get pods -A
kubectl describe pod <pod>
kubectl logs <pod>
```

Recursos:

```bash
kubectl top nodes
kubectl top pods -A
```

Charts instalados:

```bash
helm list -A
```

Render local:

```bash
helm lint bank-flow-transfer/k8s
helm template bank-flow-transfer bank-flow-transfer/k8s
```

Datasources do Grafana:

```bash
kubectl get configmap -n monitoring -l grafana_datasource=1 -o yaml
kubectl logs -n monitoring deployment/monitoring-grafana
```

ServiceMonitor e Probe:

```bash
kubectl get servicemonitor
kubectl get probe
```

Kafka:

```bash
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

## 16. Remover tudo

Servicos:

```bash
helm uninstall bank-flow-transfer
helm uninstall bank-flow-ledger
helm uninstall bank-flow-balance
helm uninstall bank-flow-accounts
```

Observabilidade:

```bash
helm uninstall tempo -n monitoring
helm uninstall alloy -n monitoring
helm uninstall loki -n monitoring
helm uninstall prometheus-blackbox-exporter -n monitoring
helm uninstall monitoring -n monitoring
```

Cluster:

```bash
minikube delete
```

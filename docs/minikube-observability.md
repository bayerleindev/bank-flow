# Minikube Observability

Este guia instala a stack de observabilidade no Minikube com:

- kube-prometheus-stack: Prometheus, Grafana, Alertmanager, kube-state-metrics e node-exporter
- Tempo: traces
- Loki: logs
- Alloy: coleta OTLP e encaminhamento para Tempo/Loki
- Dashboards do Bank Flow

## Pre-requisitos

Minikube rodando com recursos suficientes:

```bash
minikube status
kubectl cluster-info
```

Helm instalado:

```bash
helm version
```

Namespace:

```bash
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
```

Repos Helm:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
```

## 1. Instalar kube-prometheus-stack

Crie os values locais:

```bash
cat > /tmp/kube-prometheus-stack-minikube.yaml <<'EOF'
grafana:
  adminUser: admin
  adminPassword: admin
  sidecar:
    dashboards:
      enabled: true
      label: grafana_dashboard
      labelValue: "1"
      searchNamespace: monitoring
  additionalDataSources:
    - name: Tempo
      type: tempo
      access: proxy
      url: http://tempo.monitoring.svc.cluster.local:3100
      isDefault: false
      jsonData:
        tracesToMetrics:
          datasourceUid: prometheus
        serviceMap:
          datasourceUid: prometheus
    - name: Loki
      type: loki
      access: proxy
      url: http://loki-gateway.monitoring.svc.cluster.local
      isDefault: false

prometheus:
  prometheusSpec:
    serviceMonitorSelectorNilUsesHelmValues: false
    podMonitorSelectorNilUsesHelmValues: false
    retention: 2d
    resources:
      requests:
        cpu: 250m
        memory: 512Mi
      limits:
        cpu: "1"
        memory: 1536Mi

alertmanager:
  enabled: true

kube-state-metrics:
  enabled: true

prometheus-node-exporter:
  enabled: true
EOF
```

Instale:

```bash
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f /tmp/kube-prometheus-stack-minikube.yaml
```

Verifique:

```bash
kubectl get pods -n monitoring
kubectl get svc -n monitoring
```

## 2. Instalar Tempo

Crie os values:

```bash
cat > /tmp/tempo-minikube.yaml <<'EOF'
tempo:
  reportingEnabled: false
  metricsGenerator:
    enabled: true
    remoteWriteUrl: http://kube-prometheus-stack-prometheus.monitoring.svc.cluster.local:9090/api/v1/write
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

service:
  type: ClusterIP

resources:
  requests:
    cpu: 100m
    memory: 256Mi
  limits:
    cpu: 500m
    memory: 768Mi
EOF
```

Instale:

```bash
helm upgrade --install tempo grafana/tempo \
  --namespace monitoring \
  -f /tmp/tempo-minikube.yaml
```

## 3. Instalar Loki

Para Minikube, use Loki em modo simples:

```bash
cat > /tmp/loki-minikube.yaml <<'EOF'
deploymentMode: SingleBinary

loki:
  auth_enabled: false
  commonConfig:
    replication_factor: 1
  storage:
    type: filesystem
  schemaConfig:
    configs:
      - from: "2024-01-01"
        store: tsdb
        object_store: filesystem
        schema: v13
        index:
          prefix: loki_index_
          period: 24h

singleBinary:
  replicas: 1
  resources:
    requests:
      cpu: 100m
      memory: 256Mi
    limits:
      cpu: 500m
      memory: 768Mi

read:
  replicas: 0
backend:
  replicas: 0
write:
  replicas: 0

gateway:
  enabled: true
EOF
```

Instale:

```bash
helm upgrade --install loki grafana/loki \
  --namespace monitoring \
  -f /tmp/loki-minikube.yaml
```

## 4. Instalar Alloy

Alloy recebe OTLP das apps e encaminha:

- traces para Tempo
- logs para Loki

Crie os values:

```bash
cat > /tmp/alloy-minikube.yaml <<'EOF'
alloy:
  configMap:
    create: true
    content: |
      otelcol.receiver.otlp "default" {
        grpc {
          endpoint = "0.0.0.0:4317"
        }
        http {
          endpoint = "0.0.0.0:4318"
        }

        output {
          traces = [otelcol.exporter.otlp.tempo.input]
        }
      }

      otelcol.exporter.otlp "tempo" {
        client {
          endpoint = "tempo.monitoring.svc.cluster.local:4317"
          tls {
            insecure = true
          }
        }
      }

      loki.source.kubernetes "pods" {
        forward_to = [loki.write.default.receiver]
      }

      loki.write "default" {
        endpoint {
          url = "http://loki-gateway.monitoring.svc.cluster.local/loki/api/v1/push"
        }
      }

controller:
  type: daemonset

service:
  enabled: true
  type: ClusterIP
  ports:
    - name: otlp-grpc
      port: 4317
      targetPort: 4317
      protocol: TCP
    - name: otlp-http
      port: 4318
      targetPort: 4318
      protocol: TCP
EOF
```

Instale:

```bash
helm upgrade --install alloy grafana/alloy \
  --namespace monitoring \
  -f /tmp/alloy-minikube.yaml
```

## 5. Publicar dashboards do Bank Flow

O Grafana do `kube-prometheus-stack` vai carregar ConfigMaps com label `grafana_dashboard=1`.

Do diretorio raiz do repo:

```bash
for service in bank-flow-*/dashboards; do
  name="$(dirname "$service")"
  kubectl create configmap "${name}-dashboards" \
    --namespace monitoring \
    --from-file="$service" \
    --dry-run=client -o yaml \
    | kubectl label --local -f - grafana_dashboard=1 -o yaml \
    | kubectl apply -f -
done
```

Verifique:

```bash
kubectl get configmap -n monitoring -l grafana_dashboard=1
```

## 6. Conectar apps Bank Flow

As apps precisam expor `/actuator/prometheus` em Services Kubernetes e ter `ServiceMonitor`.

Exemplo para um servico:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: bank-flow-example
  namespace: monitoring
spec:
  namespaceSelector:
    matchNames:
      - bank-flow
  selector:
    matchLabels:
      app: bank-flow-example
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 10s
```

As apps tambem devem enviar traces para Alloy:

```text
MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://alloy.monitoring.svc.cluster.local:4318/v1/traces
MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
```

## 7. Acessar UIs

Grafana:

```bash
kubectl port-forward -n monitoring svc/kube-prometheus-stack-grafana 3000:80
```

Abra:

```text
http://localhost:3000
```

Login:

```text
admin / admin
```

Prometheus:

```bash
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090
```

Tempo:

```bash
kubectl port-forward -n monitoring svc/tempo 3200:3100
```

Loki:

```bash
kubectl port-forward -n monitoring svc/loki-gateway 3100:80
```

## 8. Validacao

Pods:

```bash
kubectl get pods -n monitoring
```

Targets do Prometheus:

```bash
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090
```

Depois acesse:

```text
http://localhost:9090/targets
```

Metricas geradas por traces do Tempo:

```bash
curl -s "http://localhost:9090/api/v1/query?query=traces_spanmetrics_calls_total" | jq .
curl -s "http://localhost:9090/api/v1/query?query=traces_service_graph_request_total" | jq .
```

No Grafana:

- Abra `Dashboards`
- Procure pelos dashboards `Bank Flow`
- Abra `Explore`
- Selecione `Tempo` para traces
- Selecione `Loki` para logs

## 9. Atualizar a stack

Atualizar kube-prometheus-stack:

```bash
helm upgrade kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f /tmp/kube-prometheus-stack-minikube.yaml
```

Atualizar Tempo, Loki e Alloy:

```bash
helm upgrade tempo grafana/tempo -n monitoring -f /tmp/tempo-minikube.yaml
helm upgrade loki grafana/loki -n monitoring -f /tmp/loki-minikube.yaml
helm upgrade alloy grafana/alloy -n monitoring -f /tmp/alloy-minikube.yaml
```

Atualizar dashboards:

```bash
for service in bank-flow-*/dashboards; do
  name="$(dirname "$service")"
  kubectl create configmap "${name}-dashboards" \
    --namespace monitoring \
    --from-file="$service" \
    --dry-run=client -o yaml \
    | kubectl label --local -f - grafana_dashboard=1 -o yaml \
    | kubectl apply -f -
done
```


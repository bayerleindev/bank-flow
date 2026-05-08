# Aprendizados de Kubernetes e Observabilidade

Este documento registra os principais aprendizados ao preparar os servicos do Bank Flow para deploy individualizado no Minikube com Helm, Prometheus, Grafana, Loki, Alloy e Tempo.

## 1. Deploy individual por servico

Cada servico precisa ter seu proprio chart Helm para poder ser instalado, atualizado e escalado de forma independente. Isso evita que uma mudanca no `ledger`, por exemplo, force redeploy de `accounts`, `transfer` e `balance`.

O padrao adotado foi:

```text
bank-flow-<servico>/k8s/
  Chart.yaml
  values.yaml
  templates/
```

As configuracoes ficam em `values.yaml`, separando:

- imagem e tag;
- variaveis de ambiente;
- secrets;
- probes;
- recursos;
- replicas;
- objetos de observabilidade.

## 2. Kubernetes nao usa imagem local automaticamente

Uma imagem criada no Docker local nao fica disponivel automaticamente dentro do Minikube. O cluster roda em outro ambiente, entao a imagem precisa estar em um registry ou ser carregada para dentro do Minikube.

Para imagens locais:

```bash
cd bank-flow-ledger
./gradlew bootBuildImage --imageName=bank-flow-ledger:local
minikube image load bank-flow-ledger:local
```

Depois disso, o chart pode usar:

```yaml
image:
  repository: bank-flow-ledger
  tag: local
  pullPolicy: IfNotPresent
```

## 3. StatefulSet foi melhor para o ledger

O `ledger` usa `ID_GENERATOR_WORKER_ID` para gerar IDs. Com `Deployment`, os pods nao tem identidade estavel. Com `StatefulSet`, os nomes seguem o padrao:

```text
bank-flow-ledger-0
bank-flow-ledger-1
bank-flow-ledger-2
```

O app passou a derivar o worker id do ordinal do pod. Assim, `bank-flow-ledger-0` usa worker `0`, `bank-flow-ledger-1` usa worker `1`, e assim por diante.

Limite pratico: manter no maximo 100 replicas do ledger, porque o worker id aceito vai de `0` a `99`.

## 4. Configuracao aleatoria em Helm nao e boa para worker id

Usar algo como:

```yaml
ID_GENERATOR_WORKER_ID: {{ randInt 1000 9999 | quote }}
```

nao e recomendado. O Helm recalcula valores em renderizacoes futuras, entao um `helm upgrade` pode mudar o worker id sem relacao com o pod real. Isso quebra previsibilidade e pode causar colisao ou inconsistencias na geracao de IDs.

Para IDs por replica, prefira identidade estavel do Kubernetes: `StatefulSet` + ordinal do pod.

## 5. Dependencias fora do Minikube precisam de endereco acessivel pelo cluster

Quando o Kafka, Postgres ou immudb rodam fora do Minikube, `localhost` dentro do pod nao aponta para a maquina host. Para o Minikube acessar servicos do host, usamos:

```text
host.minikube.internal
```

Exemplos:

```yaml
KAFKA_BOOTSTRAP_SERVERS: host.minikube.internal:9094
IMMUDB_HOST: host.minikube.internal
POSTGRES_URL: jdbc:postgresql://host.minikube.internal:5432/bank_flow
```

No Kafka, alem da porta, tambem e necessario anunciar um listener que faca sentido para o Minikube:

```text
MINIKUBE://host.minikube.internal:9094
```

## 6. ServiceMonitor substitui scrape estatico do prometheus.yml

No Docker Compose, o Prometheus usava `observability/prometheus/prometheus.yml` com `scrape_configs` estaticos. No Kubernetes com `kube-prometheus-stack`, o caminho correto e usar `ServiceMonitor`.

O `ServiceMonitor` seleciona um `Service` Kubernetes por labels e informa:

- porta;
- path de metricas;
- intervalo;
- labels que devem ser preservados.

Para manter compatibilidade com dashboards antigos, o `ledger` preserva labels como:

```text
job="bank-flow-services"
service="bank-flow-ledger"
stack="bank-flow"
```

Isso evita `no data` em dashboards que ja filtravam por esses labels.

## 7. Blackbox Exporter precisa de Probe

Metricas como:

```promql
probe_success{job="bank-flow-health"}
probe_http_status_code{job="bank-flow-health"}
```

nao vem do `/actuator/prometheus`. Elas vem do Blackbox Exporter.

No Kubernetes, o caminho e:

```text
Probe -> blackbox-exporter -> GET /actuator/health -> Prometheus
```

Por isso o chart do `ledger` cria um recurso `Probe` apontando para:

```text
http://bank-flow-ledger.<namespace>.svc:8085/actuator/health
```

## 8. Dashboards precisam combinar com os labels reais

Quando um dashboard mostra `no data`, a primeira validacao deve ser no Prometheus, nao no Grafana.

Exemplos:

```promql
up{job="bank-flow-services", service="bank-flow-ledger"}
process_uptime_seconds{job="bank-flow-services", service="bank-flow-ledger"}
probe_success{job="bank-flow-health", service="bank-flow-ledger"}
```

Se a query funciona no Prometheus e nao no Grafana, o problema tende a ser datasource ou variavel do dashboard. Se nao funciona no Prometheus, o problema esta no scrape, no ServiceMonitor, no Probe ou nos labels.

## 9. Loki coleta logs; Alloy substitui Promtail

Promtail foi substituido por Grafana Alloy. O Alloy roda como `DaemonSet`, descobre pods no Kubernetes, processa logs CRI, extrai campos JSON dos logs estruturados e envia para o Loki.

Fluxo:

```text
pod logs -> Alloy -> Loki -> Grafana
```

O Alloy extrai campos como:

```text
application
level
logger
trace_id
span_id
```

Assim da para consultar no Loki:

```logql
{application="bank-flow-ledger"}
{stack="bank-flow"}
{level="ERROR"}
```

## 10. Tempo recebe traces via OTLP

Os servicos Spring enviam traces via OTLP HTTP. Dentro do cluster, o endpoint do `ledger` passou a ser:

```text
http://tempo.monitoring.svc.cluster.local:4318/v1/traces
```

Fluxo:

```text
Spring Boot -> OTLP HTTP -> Tempo -> Grafana
```

Para service graph e span metrics, o Tempo precisa enviar metricas para o Prometheus via remote write. Por isso o `kube-prometheus-stack` foi configurado com:

```yaml
prometheus:
  prometheusSpec:
    enableRemoteWriteReceiver: true
```

## 11. Datasources do Grafana devem ser simples primeiro

O `kube-prometheus-stack` ja cria Prometheus e Alertmanager como datasources. Ao adicionar Loki e Tempo, nao e necessario recriar Prometheus em `additionalDataSources`.

Problemas encontrados:

- dois datasources marcados como default quebram o startup do Grafana;
- recriar Prometheus com o mesmo nome pode gerar conflito;
- referencias cruzadas como `Loki -> Tempo` ou `Tempo -> Loki` podem falhar se o datasource referenciado ainda nao existir durante o provisioning.

Decisao adotada: provisionar primeiro datasources simples:

```text
Prometheus: criado pelo kube-prometheus-stack
Loki: adicionado em additionalDataSources
Tempo: adicionado em additionalDataSources
```

Depois que tudo estiver estavel, correlacoes trace/log podem ser reintroduzidas com cuidado.

## 12. Logs locais do Kafka podem poluir a saida

O log:

```text
AdminMetadataManager Rebootstrapping with Cluster(id = null, nodes = [localhost:9092])
```

indica que o `AdminClient` esta tentando obter metadata do Kafka. Pode ser apenas ruido durante startup, mas tambem pode indicar Kafka indisponivel.

Validacao:

```bash
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

Para reduzir ruido, os servicos configuram:

```properties
logging.level.org.apache.kafka.clients.admin.internals.AdminMetadataManager=WARN
```

## 13. Memoria de imagem Paketo precisa de margem

Ao rodar imagens criadas com buildpacks Paketo, o memory calculator pode falhar se o limite do container for muito baixo.

Erro visto:

```text
fixed memory regions require ... greater than 512M available
```

Mitigacao adotada:

```yaml
resources:
  limits:
    memory: 768Mi

env:
  BPL_JVM_THREAD_COUNT: "50"
```

Isso reduz a estimativa de memoria de threads e da margem para a JVM iniciar.

## 14. Containers nao devem escrever em ../logs por padrao

Localmente, os servicos escrevem arquivos em:

```text
../logs/<service>.log
```

No container, esse caminho pode nao existir. Para Kubernetes, o chart define:

```yaml
LOG_FILE: ""
```

Assim os logs seguem para stdout/stderr, que e o caminho correto para coleta por Alloy.

## 15. Ordem recomendada para subir observabilidade no Minikube

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  -f observability/k8s/kube-prometheus-stack-values.yaml

helm upgrade --install loki grafana/loki \
  --namespace monitoring \
  -f observability/k8s/loki-values.yaml

helm upgrade --install alloy grafana/alloy \
  --namespace monitoring \
  -f observability/k8s/alloy-values.yaml

helm upgrade --install tempo grafana/tempo \
  --namespace monitoring \
  -f observability/k8s/tempo-values.yaml

helm upgrade --install prometheus-blackbox-exporter prometheus-community/prometheus-blackbox-exporter \
  --namespace monitoring

helm upgrade --install bank-flow-ledger bank-flow-ledger/k8s
```

## 16. Checklist de troubleshooting

Prometheus:

```bash
kubectl get servicemonitor
kubectl get probe
```

Queries:

```promql
up{service="bank-flow-ledger"}
probe_success{service="bank-flow-ledger"}
```

Grafana:

```bash
kubectl get configmap -n monitoring -l grafana_datasource=1 -o yaml
kubectl logs -n monitoring deployment/monitoring-grafana
```

Loki:

```bash
kubectl logs -n monitoring daemonset/alloy
kubectl get svc -n monitoring | grep loki
```

Tempo:

```bash
kubectl get svc -n monitoring | grep tempo
kubectl logs -n monitoring deployment/tempo
```

Ledger:

```bash
kubectl exec -it bank-flow-ledger-0 -- printenv | grep OTEL
kubectl logs bank-flow-ledger-0
```

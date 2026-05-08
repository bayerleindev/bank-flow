# Aprendizados do Deploy no Minikube

Esta doc registra os aprendizados do processo de preparar o Bank Flow para rodar no Minikube. Ela complementa o passo a passo operacional em `docs/deploy-kubernetes-minikube.md`.

Para validar comportamento funcional depois do deploy, use tambem `docs/fluxos-regras-validacoes.md`, que documenta fluxos, regras de negocio, validacoes e idempotencia.

## 1. Helm vira a interface principal de deploy

O principal aprendizado foi separar o deploy de cada servico em um chart Helm proprio. Isso deixa claro quais configuracoes pertencem a cada aplicacao: imagem, variaveis, secrets, probes, service, metricas e recursos.

Sem Helm, cada mudanca exige editar YAMLs manualmente. Com Helm, o fluxo fica mais previsivel:

```bash
helm upgrade --install bank-flow-transfer bank-flow-transfer/k8s
```

O `values.yaml` passa a ser o contrato operacional do servico.

## 2. Minikube nao compartilha automaticamente o Docker local

Buildar uma imagem local nao basta. O Minikube roda em outro contexto e precisa receber a imagem explicitamente.

Aprendizado pratico:

```bash
./gradlew bootBuildImage --imageName=bank-flow-transfer:local
minikube image load bank-flow-transfer:local
```

Se a imagem nao for carregada, o pod fica em `ImagePullBackOff` ou tenta buscar uma imagem que nao existe em registry externo.

## 3. Localhost muda de significado dentro do pod

Dentro de um pod, `localhost` e o proprio container, nao a maquina host. Como Postgres, Kafka e immudb ficaram fora do Minikube, os servicos dentro do cluster precisam usar:

```text
host.minikube.internal
```

Isso afetou principalmente:

```text
POSTGRES_URL
KAFKA_BOOTSTRAP_SERVERS
IMMUDB_HOST
```

Para Kafka, alem do host, foi necessario expor um listener especifico para o Minikube, porque Kafka depende fortemente de `advertised.listeners`.

## 4. Observabilidade em Kubernetes nao deve depender de prometheus.yml estatico

No Docker Compose, fazia sentido ter:

```text
observability/prometheus/prometheus.yml
```

com targets estaticos. No Kubernetes, esse modelo fica fragil. A forma correta com `kube-prometheus-stack` e usar recursos CRD:

```text
ServiceMonitor
Probe
```

`ServiceMonitor` cobre metricas internas do app:

```text
/actuator/prometheus
```

`Probe` cobre health externo via Blackbox Exporter:

```text
/actuator/health
```

## 5. Dashboard `no data` quase sempre e problema de label

Quando o Grafana mostra `no data`, o primeiro impulso pode ser mexer no dashboard. O melhor caminho e consultar direto no Prometheus.

Exemplo:

```promql
up{job="bank-flow-services", service="bank-flow-transfer"}
```

Se nao existe serie, o problema esta no scrape ou nos labels. No nosso caso, os dashboards antigos esperavam:

```text
job="bank-flow-services"
service="bank-flow-..."
stack="bank-flow"
```

Por isso os Services passaram a carregar labels de compatibilidade e os `ServiceMonitor` passaram a usar:

```yaml
jobLabel: prometheus-job
targetLabels:
  - service
  - stack
```

## 6. Blackbox Exporter nao substitui actuator metrics

O Blackbox Exporter nao coleta metricas da JVM ou HTTP server. Ele mede disponibilidade externa: status HTTP, sucesso da probe, duracao e erros de chamada.

Por isso existem dois caminhos:

```text
ServiceMonitor -> /actuator/prometheus
Probe          -> /actuator/health
```

Os dois aparecem em dashboards diferentes e respondem perguntas diferentes.

## 7. Logs em Kubernetes devem ir para stdout

Localmente, os servicos gravavam em:

```text
../logs/<servico>.log
```

No Kubernetes isso causou problema porque o caminho pode nao existir dentro da imagem. O caminho certo em containers e escrever em stdout/stderr e deixar a plataforma coletar.

Nos charts:

```yaml
LOG_FILE: ""
```

Com isso, o Grafana Alloy consegue coletar logs dos pods.

## 8. Alloy substitui Promtail com mais flexibilidade

Promtail funcionaria, mas o Alloy e o caminho mais atual da Grafana para coletar, processar e encaminhar sinais de observabilidade.

O fluxo ficou:

```text
logs do pod -> Alloy -> Loki -> Grafana
```

O Alloy foi configurado como `DaemonSet`, porque logs vivem nos nos do cluster. Ele descobre pods, processa logs CRI e extrai campos JSON dos logs estruturados.

## 9. Tempo precisa estar no endpoint dos servicos

Os servicos Spring enviam traces por OTLP HTTP. Quando Tempo roda dentro do cluster, o endpoint correto passa a ser DNS de service Kubernetes:

```text
http://tempo.monitoring.svc.cluster.local:4318/v1/traces
```

Se o app ainda aponta para `localhost` ou `host.minikube.internal`, o trace nao chega no Tempo do cluster.

## 10. Datasources do Grafana precisam ser provisionados com cuidado

O `kube-prometheus-stack` ja cria Prometheus e Alertmanager. Ao adicionar Loki e Tempo, nao se deve criar outro Prometheus default.

Erros encontrados:

```text
Only one datasource per organization can be marked as default
Datasource provisioning error: data source not found
```

Aprendizado: primeiro provisionar datasources simples. Depois adicionar correlacoes trace/log se necessario.

Estado correto inicial:

```text
Prometheus: criado pelo kube-prometheus-stack
Loki: adicional
Tempo: adicional
```

## 11. StatefulSet resolve identidade do ledger

O `ledger` precisava de um `worker_id` estavel para o gerador de IDs. Usar valor aleatorio no Helm parecia simples, mas nao e confiavel: em upgrades futuros o valor pode mudar.

Com `StatefulSet`, o Kubernetes fornece identidade estavel:

```text
bank-flow-ledger-0
bank-flow-ledger-1
```

O app pode extrair o ordinal e usar como worker id.

## 12. Java com Paketo precisa de memoria realista

O erro do memory calculator mostrou que 512Mi era pouco para os containers Java com buildpacks:

```text
fixed memory regions require ... greater than 512M available
```

Mitigacoes:

```yaml
resources:
  limits:
    memory: 768Mi

env:
  BPL_JVM_THREAD_COUNT: "50"
```

Isso nao e tuning final de producao, mas e suficiente para simular no Minikube.

## 13. Minikube pequeno nao aguenta a stack inteira

Quatro apps Java mais Prometheus, Grafana, Loki, Alloy, Tempo e Blackbox consomem bastante memoria.

Para evitar instabilidade:

```bash
minikube start --memory=8192 --cpus=4 --disk-size=30g
```

Melhor ainda:

```bash
minikube start --memory=12288 --cpus=4 --disk-size=40g
```

Tambem ficou claro que subir por etapas ajuda:

1. Dependencias externas no Docker Compose.
2. Aplicacoes.
3. Prometheus/Grafana.
4. Blackbox.
5. Loki/Alloy.
6. Tempo.

## 14. Ordem de diagnostico importa

Quando algo falha, a ordem mais eficiente foi:

1. Pod esta rodando?
2. Service existe?
3. Endpoint responde?
4. ServiceMonitor/Probe existe?
5. Prometheus ve a serie?
6. Grafana esta usando o datasource certo?

Comandos uteis:

```bash
kubectl get pods -A
kubectl describe pod <pod>
kubectl logs <pod>
kubectl get svc
kubectl get servicemonitor
kubectl get probe
kubectl top pods -A
```

Queries uteis:

```promql
up{job="bank-flow-services"}
probe_success{job="bank-flow-health"}
```

## 15. Separar runbook de aprendizados ajuda

O runbook responde "como executar":

```text
docs/deploy-kubernetes-minikube.md
```

Esta doc responde "o que aprendemos e por que fizemos assim".

Essa separacao evita misturar comandos operacionais com explicacoes longas e facilita usar a documentacao tanto para executar quanto para revisar decisoes tecnicas.

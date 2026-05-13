# Kubernetes Autoscaling e Disruption Budgets

Este documento descreve como os componentes do Bank Flow escalam no Kubernetes e quais garantias de disponibilidade sao aplicadas com `PodDisruptionBudget`.

O projeto usa dois mecanismos de escala:

- `HorizontalPodAutoscaler` para componentes HTTP ou CPU-bound.
- KEDA `ScaledObject` para componentes orientados a Kafka lag.

Nao crie HPA manual para um workload ja controlado por KEDA. O `ScaledObject` cria e gerencia o HPA internamente.

## Resumo por componente

| Componente | Workload | Escala por | Min | Max | PDB | Observacao |
| --- | --- | --- | --- | --- | --- | --- |
| `bank-flow-accounts` | `Deployment` | HPA CPU 70% | 1 | 5 | `minAvailable: 1` | API HTTP de criacao/consulta de contas. |
| `bank-flow-outboxer` | `Deployment` | HPA CPU 70% | 1 | 6 | `minAvailable: 1` | Publisher central de outbox. Locks no banco evitam dupla publicacao. |
| `bank-flow-yield` | `Deployment` | HPA CPU 70% | 1 | 4 | `minAvailable: 1` | Fecha rendimento D-1, consulta CDI e grava eventos no outbox central. |
| `bank-flow-transfer-api` | `Deployment` | HPA CPU 70% | 1 | 6 | `minAvailable: 1` | API HTTP de transferencias e webhooks. |
| `bank-flow-transfer-worker` | `Deployment` | KEDA Kafka lag | 1 | 12 | `maxUnavailable: 1` | Consome `ledger-posting-created`. |
| `bank-flow-balance-api` | `Deployment` | HPA CPU 70% | 1 | 6 | `minAvailable: 1` | API HTTP de saldos, extratos e holds. |
| `bank-flow-balance-worker` | `Deployment` | KEDA Kafka lag | 0 | 12 | desabilitado | Consome `ledger-posting-created`; pode ir a zero quando nao ha lag. |
| `bank-flow-ledger` | `StatefulSet` | KEDA Kafka lag | 1 | 4 | `maxUnavailable: 1` | Consome `account-created`, `ledger-movements`, `ledger-reversals` e `yield-accruals`. |

## Quando usar HPA

Use HPA para servicos que recebem trafego HTTP direto ou fazem polling sem uma metrica externa clara de fila.

Charts atuais com HPA:

- `bank-flow-accounts`
- `bank-flow-outboxer`
- `bank-flow-yield`
- `bank-flow-transfer-api`
- `bank-flow-balance-api`

Todos usam:

```text
autoscaling.enabled: true
targetCPUUtilizationPercentage: 70
```

Os `Deployment` desses componentes omitem `spec.replicas` quando `autoscaling.enabled=true`, deixando o HPA controlar a quantidade de replicas.

## Quando usar KEDA

Use KEDA para consumidores Kafka. A metrica que interessa nesses casos e backlog, nao CPU.

Charts atuais com KEDA:

- `bank-flow-transfer-worker`
- `bank-flow-balance-worker`
- `bank-flow-ledger`

Cada `ScaledObject` define:

```text
pollingInterval: 30
cooldownPeriod: 300
lagThreshold: "5"
activationLagThreshold: "3"
```

`lagThreshold` controla a escala quando existe backlog. `activationLagThreshold` evita ativar escala por ruidos pequenos.

## KEDA por componente

### bank-flow-transfer-worker

Escala pelo lag do topico:

```text
topic: ledger-posting-created
consumerGroup: bank-flow-transfer-worker
minReplicaCount: 1
maxReplicaCount: 12
```

Este worker captura holds e conclui transferencias apos o ledger publicar `ledger-posting-created`.

O minimo fica em `1` para reduzir latencia de conclusao de transferencias e manter o consumer group quente.

### bank-flow-balance-worker

Escala pelo lag do topico:

```text
topic: ledger-posting-created
consumerGroup: bank-flow-balance-worker
minReplicaCount: 0
maxReplicaCount: 12
```

Este worker projeta saldo e extrato. Ele pode escalar para zero quando nao ha eventos pendentes, porque nao atende trafego HTTP e sua carga principal vem do Kafka.

Como `minReplicaCount=0`, o PDB fica desabilitado para este componente. Um PDB com disponibilidade minima nao combina com escala para zero.

### bank-flow-ledger

Escala pelo lag dos topicos:

```text
topic: account-created
consumerGroup: bank-flow-ledger

topic: ledger-movements
consumerGroup: bank-flow-ledger

topic: ledger-reversals
consumerGroup: bank-flow-ledger

topic: yield-accruals
consumerGroup: bank-flow-ledger

minReplicaCount: 1
maxReplicaCount: 4
```

O ledger usa `StatefulSet` porque o identificador numerico depende do ordinal do pod. Mantenha `maxReplicaCount` dentro do limite aceito pelo gerador de ids. Hoje o limite operacional documentado do projeto e ate 100 replicas.

O minimo fica em `1` para manter processamento de eventos criticos sempre disponivel.

## PodDisruptionBudget

PDBs protegem contra interrupcoes voluntarias, como drain de node e upgrades.

Padroes atuais:

- APIs HTTP: `minAvailable: 1`
- `bank-flow-outboxer`: `minAvailable: 1`
- Workers com replica minima positiva: `maxUnavailable: 1`
- Worker que escala para zero: PDB desabilitado

Esses PDBs nao impedem falhas involuntarias, como crash de container ou perda abrupta de node. Eles apenas informam ao Kubernetes quantos pods podem ser removidos voluntariamente ao mesmo tempo.

## Cuidados operacionais

1. Instale KEDA antes dos charts que criam `ScaledObject`.
2. Nao aplique HPA separado em workloads controlados por KEDA.
3. Garanta requests de CPU nos containers; HPA por CPU depende de `resources.requests.cpu`.
4. Para consumidores Kafka, revise o numero de particoes antes de aumentar `maxReplicaCount`.
5. Para o ledger, revise tambem o limite de `ID_GENERATOR_WORKER_ID` antes de aumentar o maximo.
6. Evite `minAvailable: 1` em componentes que podem escalar para zero.

## Validacao

Renderizar os charts:

```bash
helm template bank-flow-accounts bank-flow-accounts/k8s
helm template bank-flow-outboxer bank-flow-outboxer/k8s
helm template bank-flow-yield bank-flow-yield/k8s
helm template bank-flow-balance bank-flow-balance/k8s
helm template bank-flow-ledger bank-flow-ledger/k8s
helm template bank-flow-transfer bank-flow-transfer/k8s
```

Validar os charts:

```bash
helm lint bank-flow-accounts/k8s
helm lint bank-flow-outboxer/k8s
helm lint bank-flow-yield/k8s
helm lint bank-flow-balance/k8s
helm lint bank-flow-ledger/k8s
helm lint bank-flow-transfer/k8s
```

Checar KEDA no cluster:

```bash
kubectl get crd scaledobjects.keda.sh
kubectl get scaledobject -n bank-flow
kubectl get hpa -n bank-flow
```

Checar PDBs:

```bash
kubectl get pdb -n bank-flow
```

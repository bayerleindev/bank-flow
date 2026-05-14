# Scaling APIs and Workers

This project is currently sized for a baseline of about 200k business operations per day.

That is roughly 2.3 operations per second on average. The Kubernetes values keep enough warm capacity for normal traffic and leave headroom for short bursts, retries, and asynchronous backlog without opening excessive database connections.

## Scaling Model

| Component | Scaling mechanism | Baseline |
| --- | --- | --- |
| `bank-flow-accounts` | HPA on CPU and memory | 2 to 6 pods |
| `bank-flow-transfer-api` | HPA on CPU and memory | 2 to 6 pods |
| `bank-flow-balance-api` | HPA on CPU and memory | 2 to 6 pods |
| `bank-flow-outboxer` | HPA on CPU and memory | 2 to 4 pods |
| `bank-flow-ledger` | KEDA on Kafka lag | 2 to 4 pods |
| `bank-flow-balance-worker` | KEDA on Kafka lag | 1 to 6 pods |
| `bank-flow-transfer-worker` | KEDA on Kafka lag | 1 to 6 pods |
| `bank-flow-yield` | Fixed replica | 1 pod |

`bank-flow-yield` stays fixed at one replica because it owns a scheduled D-1 accrual job. Scale it only after introducing a scheduler lock or leader election.

## HPA

API services scale on:

- CPU target: 65%
- Memory target: 75%

This keeps APIs responsive under bursty traffic while preserving stable baseline replicas for availability.

## KEDA

Kafka workers scale from consumer lag:

- Main high-volume topics use `lagThreshold: 50`.
- Lower-volume topics use `lagThreshold: 25`.
- Workers stay warm with at least one pod, except ledger which keeps two pods for availability.

The effective Kafka parallelism is bounded by topic partitions and listener concurrency. The current worker defaults are:

| Worker | Listener concurrency |
| --- | ---: |
| `bank-flow-ledger` | 3 |
| `bank-flow-balance-worker` | 2 |
| `bank-flow-transfer-worker` | 2 |

Avoid setting `replicas * concurrency` much higher than the topic partition count. Idle consumers do not increase throughput.

## Hikari

Hikari pools are intentionally small per pod. Scaling horizontally with large pools can exhaust Postgres before CPU becomes the bottleneck.

| Component | Max pool size |
| --- | ---: |
| `bank-flow-accounts` | 6 |
| `bank-flow-transfer-api` | 8 |
| `bank-flow-balance-api` | 8 |
| `bank-flow-outboxer` | 4 |
| `bank-flow-yield` | 3 |
| `bank-flow-balance-worker` | 4 |
| `bank-flow-transfer-worker` | 4 |

Common settings:

```text
HIKARI_CONNECTION_TIMEOUT_MS=2000
HIKARI_IDLE_TIMEOUT_MS=600000
HIKARI_MAX_LIFETIME_MS=1500000
```

Watch these Grafana panels after changing replicas or pool sizes:

- Hikari pool saturation
- Hikari pending connections
- HTTP p95 latency
- Kafka consumer lag
- Outbox oldest pending age
- Balance projection lag
- Postgres CPU and connection count

## When To Increase Capacity

Increase API `maxReplicas` when HTTP p95 latency is high while Hikari saturation is low.

Increase worker `maxReplicaCount` or listener concurrency when Kafka lag grows and per-pod CPU/database usage is not saturated.

Increase Hikari pool sizes only when requests wait for database connections and Postgres still has enough connection and CPU headroom.

If Hikari saturation and Postgres CPU are both high, optimize queries or add database capacity before increasing pods.

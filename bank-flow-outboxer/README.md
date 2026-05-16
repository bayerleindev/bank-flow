# bank-flow-outboxer

`bank-flow-outboxer` is the centralized transactional outbox publisher for Bank Flow. It owns the `outboxer.outbox_events` table and is the only service that claims pending outbox rows and publishes them to Kafka.

Producer services, such as `bank-flow-accounts` and `bank-flow-transfer-api`, insert rows into this table as part of their business transaction.

## Responsibilities

- Create the central `outboxer.outbox_events` table through Flyway.
- Claim pending events using `FOR UPDATE SKIP LOCKED`.
- Use a processing lease with `locked_by` and `locked_until`.
- Publish events to Kafka with a string key and JSON payload.
- Mark events as `PUBLISHED`, retry as `PENDING`, or eventually mark as `FAILED`.
- Expose health, metrics and traces.

## Runtime API

Default port: `8088`.

There is no business HTTP API. The service exposes operational endpoints:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/actuator/health` | Health endpoint. |
| `GET` | `/actuator/metrics` | Spring metrics. |
| `GET` | `/actuator/prometheus` | Prometheus scrape endpoint. |

## Table Contract

Table: `outboxer.outbox_events`

Important columns:

| Column | Purpose |
| --- | --- |
| `event_id` | Primary key. |
| `producer_service` | Source service, for example `bank-flow-accounts`. |
| `aggregate_type` | Aggregate namespace. |
| `aggregate_id` | Aggregate id. |
| `event_type` | Domain event type. |
| `topic` | Kafka topic. |
| `event_key` | Kafka message key. |
| `payload` | JSON payload. |
| `status` | `PENDING`, `PROCESSING`, `PUBLISHED` or `FAILED`. |
| `attempts` | Publish attempt count. |
| `last_error` | Last publish error, truncated. |
| `created_at` | Creation timestamp in epoch milliseconds. |
| `published_at` | Publish timestamp in epoch milliseconds. |
| `locked_by` | Publisher instance that owns the lease. |
| `locked_until` | Lease expiration in epoch milliseconds. |

Uniqueness is enforced on:

```text
producer_service, aggregate_type, aggregate_id, event_type
```

## Publish Behavior

For each claimed event, the outboxer publishes:

- Kafka topic: `topic`
- Kafka key: `event_key`
- Kafka value: `payload`
- Header `event_name`: `event_type`
- Header `content_type`: `application/json`
- Header `producer_service`: `producer_service`
- Header `transfer_id`: `aggregate_id` when `aggregate_type=Transfer`

Failed sends are retried until `OUTBOX_PUBLISHER_MAX_ATTEMPTS` is reached.

## Observability

The publisher creates explicit Kafka publish spans named by topic and event type, for example:

```text
ledger-movements publish ledger.transfer_posted
```

Important span attributes include:

- `messaging.destination.name`
- `messaging.operation`
- `messaging.kafka.message.key`
- `event.name`
- `transfer.id`, when the event belongs to a transfer

Outbox metrics used by dashboards include pending events, oldest pending event age, published events and publish failures.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8088` | Actuator HTTP port. |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/bank_flow?currentSchema=outboxer,public` | Outbox datasource. |
| `POSTGRES_USER` | `myuser` | Postgres user. |
| `POSTGRES_PASSWORD` | `mysecretpassword` | Postgres password. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers. |
| `OUTBOX_PUBLISHER_FIXED_DELAY_MS` | `1000` | Delay between polling cycles. |
| `OUTBOX_PUBLISHER_BATCH_SIZE` | `50` | Max events claimed per cycle. |
| `OUTBOX_PUBLISHER_LOCK_LEASE_MS` | `60000` | Processing lease duration. |
| `OUTBOX_PUBLISHER_SEND_TIMEOUT_MS` | `30000` | Kafka send timeout. |
| `OUTBOX_PUBLISHER_MAX_ATTEMPTS` | `10` | Attempts before `FAILED`. |
| `KAFKA_PRODUCER_MAX_BLOCK_MS` | `30000` | Kafka producer max block time. |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Trace export endpoint. |

## Run Locally

```bash
docker compose up -d db kafka kafka-init
cd bank-flow-outboxer
./gradlew bootRun
```

Start this service before producer services in a clean database so Flyway creates the central outbox table first.

## Tests

```bash
cd bank-flow-outboxer
./gradlew test
```

## Operational Queries

```bash
docker compose exec -T db psql -U myuser -d bank_flow \
  -c "SELECT producer_service, topic, status, COUNT(*) FROM outboxer.outbox_events GROUP BY 1,2,3 ORDER BY 1,2,3;"
```

```bash
docker compose exec -T db psql -U myuser -d bank_flow \
  -c "SELECT event_id, producer_service, topic, status, attempts, last_error FROM outboxer.outbox_events ORDER BY created_at DESC LIMIT 20;"
```

## Contributing Notes

- Keep publishing logic centralized here.
- Avoid producer-specific branching in this service unless the event contract truly requires it.
- Add tests for claim, retry and failure behavior when changing repository or publisher logic.
- Keep producer services responsible for writing durable outbox rows in the same transaction as business state.
- Preserve trace context propagation from outbox rows into Kafka headers.

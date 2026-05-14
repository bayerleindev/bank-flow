# bank-flow-transfer

`bank-flow-transfer` orchestrates money transfers between digital accounts. It is a multi-module Spring project with an HTTP API, a Kafka worker and shared domain code.

The API writes transfer state and ledger commands to Postgres. Ledger command publication is centralized in `bank-flow-outboxer`.

## Modules

| Module | Runtime | Port | Responsibility |
| --- | --- | --- | --- |
| `:shared` | Library | n/a | Domain, services, repositories, clients, metrics and migrations. |
| `:api` | Spring Boot | `8083` | Transfer API and webhook endpoints. |
| `:worker` | Spring Boot | `8086` | Consumes ledger posting confirmations and completes transfers. |

## Responsibilities

- Receive transfer requests with `Idempotency-Key`.
- Validate source and destination accounts through `bank-flow-accounts`.
- Create balance holds through `bank-flow-balance-api`.
- Call a PSP implementation in `mock` or HTTP mode.
- Handle PSP confirmation or failure webhooks.
- Receive inbound transfers from external institutions.
- Insert `ledger-movements` into `outboxer.outbox_events`.
- Complete transfers after `ledger-posting-created`.

## Public API

Default API port: `8083`.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/transfers` | Create an internal transfer. Requires `Idempotency-Key`. |
| `GET` | `/transfers/{transfer_id}` | Return one transfer. |
| `POST` | `/webhooks/psp/transfers` | Receive PSP status updates. |
| `POST` | `/webhooks/external-institutions/transfers` | Receive inbound transfers from external institutions. |
| `GET` | `/actuator/health` | Health endpoint. |
| `GET` | `/actuator/prometheus` | Prometheus metrics. |

Create transfer:

```bash
curl -s -X POST http://localhost:8083/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: transfer-001" \
  -d '{
    "source_digital_account_id": "3f20291f-c0ba-4c8e-b0b2-7ff1cccb3833",
    "destination_digital_account_id": "530743d7-9663-453f-9ef5-3c68ec4f7929",
    "amount_minor": 1500,
    "currency": "BRL",
    "description": "Transferencia teste"
  }'
```

Confirm PSP payment:

```bash
curl -s -X POST http://localhost:8083/webhooks/psp/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "psp_payment_id": "psp-{transfer_id}",
    "status": "CONFIRMED",
    "failure_reason": null
  }'
```

Receive external inbound transfer:

```bash
curl -s -X POST http://localhost:8083/webhooks/external-institutions/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "source_institution_code": "260",
    "source_institution_name": "External Bank",
    "external_transfer_id": "evt-123",
    "destination_digital_account_id": "530743d7-9663-453f-9ef5-3c68ec4f7929",
    "amount_minor": 2500,
    "currency": "BRL",
    "description": "PIX recebido"
  }'
```

## Transfer Lifecycle

```text
POST /transfers
  -> validate accounts
  -> create transfer
  -> create balance hold
  -> create PSP payment
  -> wait for PSP webhook

PSP CONFIRMED
  -> insert ledger-movements into central outbox
  -> mark transfer POSTING_REQUESTED
  -> outboxer publishes ledger-movements
  -> ledger posts double-entry movement
  -> ledger publishes ledger-posting-created
  -> transfer-worker captures hold
  -> transfer becomes COMPLETED

PSP FAILED
  -> release hold
  -> transfer becomes FAILED
```

Inbound external transfers skip PSP and holds. They use the settlement account below as the source:

```text
source_digital_account_id: 00000000-0000-0000-0000-000000000100
source_account: SETTLEMENT_EXTERNAL_INBOUND_BRL
```

## Statuses

| Status | Meaning |
| --- | --- |
| `RECEIVED` | Request was accepted. |
| `HOLD_CREATED` | Funds were reserved. |
| `PSP_PENDING` | PSP payment is pending. |
| `PSP_CONFIRMED` | PSP confirmed the payment. |
| `POSTING_REQUESTED` | Ledger command was recorded in the outbox. |
| `COMPLETED` | Ledger posted and hold was captured. |
| `FAILED` | Transfer failed before completion. |
| `EXPIRED` | Transfer expired before completion. |
| `REVERSED` | Completed transfer was reversed. |

## Outbox Contract

`bank-flow-transfer-api` inserts ledger commands into `outboxer.outbox_events`.

| Field | Value |
| --- | --- |
| `producer_service` | `bank-flow-transfer` |
| `topic` | `ledger-movements` |
| `event_key` | `source_digital_account_id` |

Payload:

```json
{
  "transfer_id": "b6384f49-2648-4896-a07a-c09b82719b88",
  "source_digital_account_id": "3f20291f-c0ba-4c8e-b0b2-7ff1cccb3833",
  "source_account": "12345-6",
  "destination_digital_account_id": "530743d7-9663-453f-9ef5-3c68ec4f7929",
  "destination_account": "01023-0",
  "amount_cents": 1500,
  "currency": "BRL"
}
```

The worker consumes `ledger-posting-created` with key `external_id` in the
`bank-flow-transfer-worker` consumer group.

## Configuration

| Environment variable | Default | Used by |
| --- | --- | --- |
| `SERVER_PORT` | `8083` API, `8086` worker | API and worker |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/bank_flow?currentSchema=transfer,public` | API and worker |
| `POSTGRES_USER` | `myuser` | API and worker |
| `POSTGRES_PASSWORD` | `mysecretpassword` | API and worker |
| `ACCOUNTS_BASE_URL` | `http://localhost:8084` | API |
| `BALANCE_BASE_URL` | `http://localhost:8082` | API and worker |
| `PSP_MODE` | `mock` | API |
| `PSP_BASE_URL` | `http://localhost:9099` | API |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Worker |
| `KAFKA_CONSUMER_GROUP_ID` | `bank-flow-transfer-worker` | Worker |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | API and worker |

The API and worker set `bank-flow.outbox.producer-service=bank-flow-transfer`.

## Observability

Transfer trace context is persisted on transfer rows and outbox rows so the flow can remain connected across PSP webhooks, outbox publishing and Kafka consumers. The worker creates explicit Kafka consume spans named by topic and event type, with consumer group and message metadata as span attributes.

Business metrics used by dashboards include transfer status counts, transfer end-to-end latency, PSP confirmations and completed or failed transfer totals.

## Run Locally

Start shared infrastructure:

```bash
docker compose up -d db kafka kafka-init immudb
```

Run required services for the full flow:

```bash
cd bank-flow-outboxer && ./gradlew bootRun
cd bank-flow-accounts && ./gradlew bootRun
cd bank-flow-balance && ./gradlew :api:bootRun
cd bank-flow-balance && ./gradlew :worker:bootRun
cd bank-flow-ledger && ./gradlew bootRun
```

Run transfer API and worker:

```bash
cd bank-flow-transfer
./gradlew :api:bootRun
./gradlew :worker:bootRun
```

## Tests

```bash
cd bank-flow-transfer
./gradlew test
./gradlew :api:test
./gradlew :worker:test
```

## Build Images

```bash
cd bank-flow-transfer
./gradlew :api:bootBuildImage --imageName=bank-flow-transfer-api:local
./gradlew :worker:bootBuildImage --imageName=bank-flow-transfer-worker:local
```

## Contributing Notes

- Keep API contracts based on `digital_account_id`.
- Keep Kafka publishing out of this service; write outbox rows only.
- Add tests for status transitions and idempotency.
- Update event payload examples when ledger command contracts change.
- Preserve stored trace context across transfer creation, PSP webhook handling and outbox writes.

# bank-flow-balance

`bank-flow-balance` projects ledger postings into balances and statements, and manages account holds used by transfers.

It is a projection service. It does not decide accounting rules; it reflects what `bank-flow-ledger` has posted.

## Modules

| Module | Runtime | Port | Responsibility |
| --- | --- | --- | --- |
| `:shared` | Library | n/a | Domain, services, repositories, metrics and migrations. |
| `:api` | Spring Boot | `8082` | Balance, statement and hold HTTP endpoints. |
| `:worker` | Spring Boot | `8087` | Kafka consumer, projection worker and hold expiration scheduler. |

## Responsibilities

- Consume `ledger-posting-created`.
- Project posted ledger lines into account balances.
- Store statement lines.
- Enforce idempotency for processed ledger entries.
- Create, capture, release and expire holds.
- Expose balance and statement read APIs.

## Public API

Default API port: `8082`.

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/balances/{digital_account_id}` | Return current balance. |
| `GET` | `/balances/{digital_account_id}/statement` | Return paged statement lines. |
| `POST` | `/holds` | Create a balance hold. |
| `POST` | `/holds/{hold_id}/capture` | Capture a held amount. |
| `POST` | `/holds/{hold_id}/release` | Release a held amount. |
| `GET` | `/actuator/health` | Health endpoint. |
| `GET` | `/actuator/prometheus` | Prometheus metrics. |

Query balance:

```bash
curl -s http://localhost:8082/balances/{digital_account_id}
```

Query statement:

```bash
curl -s "http://localhost:8082/balances/{digital_account_id}/statement?limit=20"
```

Create hold:

```bash
curl -s -X POST http://localhost:8082/holds \
  -H "Content-Type: application/json" \
  -d '{
    "transfer_id": "b6384f49-2648-4896-a07a-c09b82719b88",
    "digital_account_id": "530743d7-9663-453f-9ef5-3c68ec4f7929",
    "amount_minor": 1000,
    "currency": "BRL",
    "reason": "TRANSFER",
    "expires_at": 4102444800000
  }'
```

Capture or release:

```bash
curl -s -X POST http://localhost:8082/holds/{hold_id}/capture
curl -s -X POST http://localhost:8082/holds/{hold_id}/release
```

## Projection Rules

- `posted_minor` changes only when a ledger posting is projected.
- `held_minor` changes when holds are created, captured, released or expired.
- `available_minor = posted_minor - held_minor`.
- `ledger-posting-created` processing is idempotent by ledger entry identity.
- Statement lines preserve the ledger `account_id` internally, but public APIs use `digital_account_id`.

## Hold Rules

- A hold can be created only when `available_minor` is sufficient.
- Capture and release are idempotent for already matching terminal states.
- Capturing a released hold is invalid.
- Releasing a captured hold is invalid.
- Expired holds are released by the worker scheduler.

## Consumed Event

Topic: `ledger-posting-created`

Key: `external_id`

The worker expects posted entries with balanced ledger lines. Example line:

```json
{
  "line_id": 1778122436160000000,
  "entry_id": 1778122436150000000,
  "account_id": 1778122358876000000,
  "digital_account_id": "530743d7-9663-453f-9ef5-3c68ec4f7929",
  "direction": "CREDIT",
  "amount_minor": 1500,
  "signed_amount_minor": 1500,
  "currency": "BRL",
  "line_memo": "destination",
  "created_at": 1778122436000
}
```

## Data Ownership

Main tables:

| Table | Purpose |
| --- | --- |
| `account_balances` | Current projected balances. |
| `account_balance_entries` | Statement lines. |
| `account_holds` | Holds and their status transitions. |
| `processed_ledger_entries` | Idempotency for consumed ledger postings. |

## Configuration

| Environment variable | Default | Used by |
| --- | --- | --- |
| `SERVER_PORT` | `8082` API, `8087` worker | API and worker |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/bank_flow` | API and worker |
| `POSTGRES_USER` | `myuser` | API and worker |
| `POSTGRES_PASSWORD` | `mysecretpassword` | API and worker |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Worker |
| `KAFKA_CONSUMER_GROUP_ID` | `bank-flow-balance-worker` | Worker |
| `KAFKA_AUTO_OFFSET_RESET` | `earliest` | Worker |
| `KAFKA_RETRY_INTERVAL_MS` | `1000` | Worker |
| `KAFKA_RETRY_MAX_ATTEMPTS` | `3` | Worker |
| `KAFKA_HEALTH_TIMEOUT_MS` | `2000` | Worker |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | API and worker |

## Run Locally

```bash
docker compose up -d db kafka kafka-init
cd bank-flow-balance
./gradlew :api:bootRun
./gradlew :worker:bootRun
```

For the full projection flow, also run `bank-flow-ledger`.

## Tests

```bash
cd bank-flow-balance
./gradlew test
./gradlew :api:test
./gradlew :worker:test
```

## Build Images

```bash
cd bank-flow-balance
./gradlew :api:bootBuildImage --imageName=bank-flow-balance-api:local
./gradlew :worker:bootBuildImage --imageName=bank-flow-balance-worker:local
```

## Contributing Notes

- Keep this service as a projection and hold service, not an accounting engine.
- Add tests for projection idempotency and hold state transitions.
- Keep public API examples aligned with controller DTOs.

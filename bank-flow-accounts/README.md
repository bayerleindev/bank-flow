# bank-flow-accounts

`bank-flow-accounts` owns digital account creation and account lookup. It talks to a BaaS provider, persists operational account data in Postgres and writes `account-created` events to the centralized outbox table.

It does not publish to Kafka directly. `bank-flow-outboxer` publishes the outbox row later.

## Responsibilities

- Receive account creation requests with `Idempotency-Key`.
- Validate account holder data.
- Create or reuse accounts idempotently.
- Call a BaaS implementation in `mock` or HTTP mode.
- Persist `baas_account_id`, `branch`, `account`, `currency` and status.
- Insert `account-created` into `outboxer.outbox_events` when the account becomes `ACTIVE`.
- Serve account lookup by `digital_account_id`.

## Public API

Default port: `8084`.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/accounts` | Create an account. Requires `Idempotency-Key`. |
| `GET` | `/accounts/{digital_account_id}` | Return one account. |
| `GET` | `/actuator/health` | Health endpoint. |
| `GET` | `/actuator/prometheus` | Prometheus metrics. |

Create account:

```bash
curl -s -X POST http://localhost:8084/accounts \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: account-001" \
  -d '{
    "fullName": "Maria Silva",
    "documentNumber": "35225454860",
    "email": "maria@example.com",
    "motherName": "Ana Silva",
    "socialName": "Maria",
    "phoneNumber": "+5511999999999",
    "birthDate": "18-12-1996",
    "address": "Rua Teste, 123",
    "isPoliticallyExposed": false
  }'
```

Lookup:

```bash
curl -s http://localhost:8084/accounts/{digital_account_id}
```

Example response:

```json
{
  "digital_account_id": "530743d7-9663-453f-9ef5-3c68ec4f7929",
  "document_number": "***4860",
  "email": "maria@example.com",
  "baas_account_id": "baas-35225454860",
  "branch": "0001",
  "account": "54860-0",
  "currency": "BRL",
  "status": "ACTIVE",
  "failure_reason": null,
  "created_at": 1778101782961,
  "updated_at": 1778101782961
}
```

## Outbox Contract

When an account becomes `ACTIVE`, this service inserts an event into `outboxer.outbox_events`.

| Field | Value |
| --- | --- |
| `producer_service` | `bank-flow-accounts` |
| `topic` | `account-created` |
| `event_type` | account creation event name from domain code |
| `event_key` | `digital_account_id` |

Payload:

```json
{
  "digital_account_id": "530743d7-9663-453f-9ef5-3c68ec4f7929",
  "branch": "0001",
  "account": "54860-0",
  "currency": "BRL"
}
```

`bank-flow-outboxer` publishes this row to Kafka. `bank-flow-ledger` consumes the Kafka event and creates the internal accounting account.

## Data Ownership

This service owns the `accounts` Postgres schema. It exposes only `digital_account_id`. Numeric accounting ids are owned by `bank-flow-ledger`.

Historical local outbox tables are dropped by migration because outbox storage is now centralized.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8084` | HTTP port. |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/bank_flow?currentSchema=accounts,public` | Accounts datasource. |
| `POSTGRES_USER` | `myuser` | Postgres user. |
| `POSTGRES_PASSWORD` | `mysecretpassword` | Postgres password. |
| `BAAS_MODE` | `mock` | `mock` or HTTP BaaS mode. |
| `BAAS_BASE_URL` | `http://localhost:9098` | HTTP BaaS base URL. |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Trace export endpoint. |

The application also sets `bank-flow.outbox.producer-service=bank-flow-accounts`.

## Run Locally

Start dependencies from the repository root:

```bash
docker compose up -d db kafka kafka-init immudb
```

Start the outboxer first in a separate terminal:

```bash
cd bank-flow-outboxer
./gradlew bootRun
```

Then run accounts:

```bash
cd bank-flow-accounts
./gradlew bootRun
```

## Tests

```bash
cd bank-flow-accounts
./gradlew test
```

## Contributing Notes

- Keep idempotency behavior intact.
- Do not reintroduce Kafka publishing into this service.
- Update this README when request or response payloads change.
- Add tests for validation, idempotency and outbox row creation when changing account creation behavior.

# bank-flow-ledger

`bank-flow-ledger` owns the double-entry accounting ledger. It consumes Kafka events, resolves digital accounts into internal accounting accounts, persists postings in immudb and publishes `ledger-posting-created`.

Only this service owns numeric accounting `account_id` values.

## Responsibilities

- Consume `account-created` and create an accounting account for each digital account.
- Consume `ledger-movements` and post balanced debit/credit entries.
- Consume `ledger-reversals` and create reversal entries.
- Persist ledger accounts, entries and lines in immudb.
- Publish `ledger-posting-created` after successful posting.
- Republish existing postings for idempotent duplicate movement commands.

## Runtime API

Default port: `8085`.

There is no public business HTTP API. The service exposes operational endpoints:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/actuator/health` | Health endpoint. |
| `GET` | `/actuator/metrics` | Spring metrics. |
| `GET` | `/actuator/prometheus` | Prometheus scrape endpoint. |

## Consumed Topics

| Topic | Key | Purpose |
| --- | --- | --- |
| `account-created` | `digital_account_id` | Create internal accounting account. |
| `ledger-movements` | `source_digital_account_id` | Post transfer movement. |
| `ledger-reversals` | `original_external_id` | Reverse an existing posting. |

## Published Topic

| Topic | Key | Purpose |
| --- | --- | --- |
| `ledger-posting-created` | `external_id` | Notify projections and workflows that a posting exists. |

## Ledger Movement Contract

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

## Posting Created Contract

```json
{
  "entry_id": 1778122436150000000,
  "external_id": "b6384f49-2648-4896-a07a-c09b82719b88",
  "entry_type": "TRANSFER",
  "status": "POSTED",
  "description": "Transferencia BRL",
  "occurred_at": 1778122436000,
  "created_at": 1778122436000,
  "reversal_of_entry_id": 0,
  "metadata": "{}",
  "lines": [
    {
      "line_id": 1778122436160000000,
      "entry_id": 1778122436150000000,
      "account_id": 1778122358876000000,
      "digital_account_id": "3f20291f-c0ba-4c8e-b0b2-7ff1cccb3833",
      "direction": "DEBIT",
      "amount_minor": 1500,
      "signed_amount_minor": -1500,
      "currency": "BRL",
      "line_memo": "source",
      "created_at": 1778122436000
    }
  ]
}
```

## Business Rules

- Every posting must have at least two lines.
- Signed line amounts must sum to zero by currency.
- `currency` is currently expected to be `BRL`.
- `external_id` is the idempotency key for postings.
- Duplicate movement commands republish the existing posting.
- A reversal uses `reversal:<original_external_id>` as its deterministic posting key.
- A reversal cannot be reversed again.
- An already reversed original posting cannot be reversed twice.

## Settlement Account

External inbound transfers use this source:

```text
source_digital_account_id: 00000000-0000-0000-0000-000000000100
source_account: SETTLEMENT_EXTERNAL_INBOUND_BRL
```

Seed it in immudb before processing inbound transfers:

```text
scripts/immudb/001_create_ledger_tables.sql
scripts/immudb/002_seed_settlement_accounts.sql
```

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8085` | Actuator HTTP port. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers. |
| `KAFKA_CONSUMER_GROUP_ID` | `bank-flow-ledger` | Consumer group. |
| `KAFKA_AUTO_OFFSET_RESET` | `latest` | Offset reset policy. |
| `KAFKA_RETRY_INTERVAL_MS` | `1000` | Retry interval. |
| `KAFKA_RETRY_MAX_ATTEMPTS` | `3` | Attempts before DLT. |
| `ID_GENERATOR_WORKER_ID` | `0` | Numeric id generator worker id. |
| `IMMUDB_ENABLED` | `true` | Enable immudb persistence. |
| `IMMUDB_HOST` | `localhost` | immudb host. |
| `IMMUDB_PORT` | `3322` | immudb gRPC port. |
| `IMMUDB_DATABASE` | `ledger` | immudb database. |
| `IMMUDB_USERNAME` | `immudb` | immudb user. |
| `IMMUDB_PASSWORD` | `immudb` | immudb password. |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Trace export endpoint. |

## Run Locally

```bash
docker compose up -d kafka kafka-init immudb
cd bank-flow-ledger
./gradlew bootRun
```

## Tests

```bash
cd bank-flow-ledger
./gradlew test
```

## Contributing Notes

- Keep double-entry invariants explicit and tested.
- Do not leak internal `account_id` ownership into other services.
- Add tests for idempotency when changing consumer behavior.
- Update event examples when Kafka contracts change.

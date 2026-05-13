# bank-flow-yield

`bank-flow-yield` is responsible for the account yield flow. The target product rule is D-1 accrual using CDI from BCB SGS series 12, with a configurable CDI percentage such as `100`, `110` or `120`.

Current implemented scope:

- Scheduled D-1 accrual using the previous calendar day as `reference_date`.
- CDI fetch from BCB SGS series 12.
- Configurable CDI percentage through `YIELD_CDI_PERCENTAGE`.
- Persistence of the CDI value used in `daily_cdi_yield_rates`.
- Persistence of per-account accruals in `account_yield_accruals`.
- Publication of `yield-accruals` through the centralized outbox.

## Topic

| Topic | Key | Purpose |
| --- | --- | --- |
| `yield-accruals` | `digital_account_id` | CDI yield accrual consumed by ledger. |
| `ledger-posting-created` | `external_id` | Ledger confirmation consumed to mark yield accruals as `POSTED`. |

## Table

Table: `yield.daily_cdi_yield_rates`

| Column | Purpose |
| --- | --- |
| `reference_date` | D-1 business date being accrued. |
| `source_rate_date` | Date of the CDI rate returned by BCB and used for the calculation. |
| `source` | Rate source, currently `BCB_SGS_12`. |
| `source_url` | BCB URL used to fetch the rate. |
| `raw_value` | Original value returned by the source. |
| `cdi_daily_rate_percent` | Daily CDI percentage from the source. |
| `cdi_daily_factor` | Daily CDI factor derived from the source value. |
| `yield_cdi_percentage` | Product multiplier, for example `100` for 100% CDI. |
| `effective_daily_factor` | Final factor after applying the multiplier. |
| `fetched_at` | When the rate was fetched. |
| `created_at` | When the row was created. |

Table: `yield.account_yield_accruals`

| Column | Purpose |
| --- | --- |
| `accrual_id` | Idempotency key for the ledger posting. |
| `reference_date` | Previous day being closed. |
| `digital_account_id` | Account that receives yield. |
| `base_balance_minor` | Balance used as calculation base in minor units. |
| `yield_amount_minor` | Calculated yield amount in minor units. |
| `currency` | Currency, currently `BRL`. |
| `cdi_daily_rate_percent` | CDI percent used for the calculation. |
| `yield_cdi_percentage` | Product multiplier used for the calculation. |
| `status` | Local processing status. |
| `created_at` | When the accrual was created. |

## D-1 Flow

1. The scheduler runs at `YIELD_ACCRUAL_CRON`, default `0 0 3 * * *`.
2. The service calculates `reference_date = today - 1` in `YIELD_ZONE_ID`.
3. It fetches CDI from BCB SGS 12 up to `reference_date`, using a short lookback window to handle weekends and holidays without CDI publication.
4. It stores the rate and effective multiplier used.
5. It reads positive BRL balances from the balance projection.
6. It creates one accrual per eligible account and emits `yield-accruals`.
7. `bank-flow-ledger` posts the expense debit and customer credit, then publishes `ledger-posting-created`.
8. `bank-flow-yield` consumes `ledger-posting-created` for `YIELD_CDI` and marks the accrual as `POSTED`.

## Test Endpoint

Trigger the same D-1 flow manually:

```bash
curl -X POST http://localhost:8089/yield/accruals/previous-day
```

Response:

```json
{
  "status": "processed"
}
```

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8089` | HTTP port. |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/bank_flow?currentSchema=yield,public` | Yield datasource. |
| `POSTGRES_USER` | `myuser` | Postgres user. |
| `POSTGRES_PASSWORD` | `mysecretpassword` | Postgres password. |
| `CDI_BASE_URL` | `https://api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados` | BCB SGS 12 endpoint. |
| `YIELD_CDI_PERCENTAGE` | `100` | CDI multiplier used by the product. |
| `CDI_LOOKBACK_DAYS` | `7` | Lookback window used to find the latest CDI rate available up to D-1. |
| `YIELD_ACCRUAL_CRON` | `0 0 3 * * *` | Schedule for the D-1 close. |
| `YIELD_ZONE_ID` | `America/Sao_Paulo` | Time zone used to calculate D-1. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers. |

## Run

```bash
docker compose up -d db kafka kafka-init
cd bank-flow-yield
./gradlew bootRun
```

## Test

```bash
cd bank-flow-yield
./gradlew test
```

# k6 load tests

This directory contains k6 scripts for exercising Bank Flow end to end.

## Heavy E2E

`heavy-e2e.js` creates a funded account pool during `setup()` and then runs four
parallel scenarios:

| Scenario | Default rate | Flow |
| --- | ---: | --- |
| `account_creation` | 10/s | `POST /accounts` |
| `external_inbound_transfers` | 150/s | external institution inbound webhook |
| `internal_transfers_with_psp` | 80/s | `POST /transfers` followed by PSP webhook |
| `balance_reads` | 200/s | `GET /balances/{digital_account_id}` |

Default URLs assume the services are exposed directly on local ports:

```bash
k6 run scripts/k6/heavy-e2e.js
```

Useful overrides:

```bash
ACCOUNTS_URL=http://localhost:8084 \
TRANSFER_URL=http://localhost:8083 \
BALANCE_URL=http://localhost:8082 \
DURATION=20m \
SEED_ACCOUNTS=100 \
EXTERNAL_RATE=300 \
INTERNAL_RATE=150 \
BALANCE_READ_RATE=400 \
k6 run scripts/k6/heavy-e2e.js
```

For Kubernetes, port-forward or point the URLs at the ingress/services before
running the same command.

The profile is intentionally heavy. Watch Grafana, Kafka lag, DLQs, outbox
pending age, Postgres connections, and immudb latency while it runs.

# BaaS Simulator

Small HTTP simulator for the accounts and transfers flows.

It exposes:

- `POST /accounts` on port `8089`, returns `202`, and asynchronously calls the accounts API webhook with either `ACTIVE` or `REJECTED`.
- `POST /pix/payments`, returns `202`, and asynchronously calls the transfers API webhook with either `COMPLETED` or `REJECTED`.
- `GET /dict?key={key}`, returns Pix account data from `pix-keys.json` plus a generated `endToEndId`.

## Run

```bash
python3 baas-simulator/app.py
```

Or from the repository root:

```bash
make baas
```

## Environment

- `BAAS_PORT`: server port. Default: `8089`
- `ACCOUNTS_WEBHOOK_URL`: accounts webhook target. Default: `http://localhost:8080/webhooks/baas/accounts`
- `WEBHOOK_DELAY_SECONDS`: delay before accounts webhook callback. Default: `8`
- `TRANSFERS_WEBHOOK_URL`: transfers webhook target. Default: `http://localhost:8083/baas/webhooks/transfers`
- `TRANSFERS_WEBHOOK_DELAY_SECONDS`: delay before transfers webhook callback. Default: `2`
- `TRANSFERS_WEBHOOK_SUCCESS_RATE`: chance of a completed transfer callback. Default: `0.85`
- `WEBHOOK_TIMEOUT_SECONDS`: webhook HTTP timeout. Default: `10`
- `WEBHOOK_MAX_ATTEMPTS`: webhook retry attempts. Default: `3`
- `WEBHOOK_RETRY_BACKOFF_SECONDS`: linear retry backoff base. Default: `1`
- `DICT_FILE`: Pix key dictionary file. Default: `baas-simulator/pix-keys.json`

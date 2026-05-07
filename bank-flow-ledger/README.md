# bank-flow-ledger

Servico responsavel pelo livro contabil double-entry. Ele consome eventos e comandos com `digital_account_id`, resolve/cria contas contabeis internas e persiste postings no immudb.

Somente este servico manipula o `account_id` numerico contabil.

## Responsabilidades

- Consumir `account-created` e criar conta contabil para o `digital_account_id`.
- Consumir `ledger-movements` e criar postings de transferencia.
- Consumir `ledger-reversals` e criar estornos.
- Persistir entries/lines no immudb de forma idempotente.
- Publicar `ledger-posting-created` com linhas contendo `account_id` e `digital_account_id`.

## Fluxo

```text
account-created
  -> ledger cria ledger_account interno

ledger-movements
  -> valida chave source_digital_account_id
  -> resolve source/destination ledger account_id
  -> cria debit/credit lines
  -> persiste no immudb
  -> publica ledger-posting-created
```

## Kafka

Consumidos:

- `account-created`, chave `digital_account_id`
- `ledger-movements`, chave `source_digital_account_id`
- `ledger-reversals`, chave `original_external_id`

Publicado:

- `ledger-posting-created`, chave `external_id`

## Contratos

`ledger-movements`:

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

`ledger-posting-created`:

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

## Configuracao

| Variavel | Padrao | Descricao |
| --- | --- | --- |
| `SERVER_PORT` | `8085` | Porta HTTP para Actuator. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers. |
| `KAFKA_AUTO_OFFSET_RESET` | `latest` | Offset reset. |
| `ID_GENERATOR_WORKER_ID` | `0` | Worker id do gerador numerico. |
| `IMMUDB_ENABLED` | `true` | Liga persistencia real. |
| `IMMUDB_HOST` | `localhost` | Host immudb. |
| `IMMUDB_PORT` | `3322` | Porta gRPC immudb. |
| `IMMUDB_DATABASE` | `ledger` | Database immudb. |
| `IMMUDB_USERNAME` | `immudb` | Usuario immudb. |
| `IMMUDB_PASSWORD` | `immudb` | Senha immudb. |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Export de traces para Tempo. |

## Observability

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

O ledger nao possui API de negocio publica, mas expoe Actuator na porta `8085`.

## Rodando

```bash
docker compose up -d kafka kafka-init immudb
cd bank-flow-ledger
./gradlew bootRun
```

Prepare as tabelas do immudb quando necessario:

```text
scripts/immudb/001_create_ledger_tables.sql
scripts/immudb/002_seed_settlement_accounts.sql
```

## Testes

```bash
cd bank-flow-ledger
./gradlew test
```

# bank-flow-balance

Servico responsavel por projetar saldos/extratos a partir do `ledger-posting-created` e por gerenciar holds de saldo usados pelo transfer-service.

A API publica usa `digital_account_id`. O `ledger_account_id` numerico pode ser armazenado internamente em linhas de extrato, mas nao e identificador publico.

## Responsabilidades

- Consumir `ledger-posting-created`.
- Projetar `account_balances` por `digital_account_id`.
- Gravar linhas historicas em `account_balance_entries`.
- Garantir idempotencia por `entry_id` e `external_id`.
- Criar, capturar e liberar holds.
- Expor saldo, extrato, health, métricas e traces.

## Fluxo de Projecao

```text
ledger-posting-created
  -> valida payload e chave external_id
  -> registra processed_ledger_entries
  -> grava account_balance_entries
  -> atualiza account_balances por digital_account_id
```

## Fluxo de Hold

```text
transfer POST /holds
  -> cria account_holds HELD por digital_account_id
  -> incrementa held_minor se available_minor for suficiente

transfer POST /holds/{hold_id}/capture
  -> muda HELD para CAPTURED
  -> decrementa held_minor

transfer POST /holds/{hold_id}/release
  -> muda HELD para RELEASED
  -> decrementa held_minor
```

## API

Porta padrao: `8082`.

Consultar saldo:

```bash
curl -s http://localhost:8082/balances/{digital_account_id}
```

Resposta:

```json
{
  "digital_account_id": "530743d7-9663-453f-9ef5-3c68ec4f7929",
  "currency": "BRL",
  "posted_minor": 10000,
  "held_minor": 100,
  "available_minor": 9900,
  "updated_at": 1778122436000
}
```

Consultar extrato:

```bash
curl -s "http://localhost:8082/balances/{digital_account_id}/statement?limit=20"
```

Criar hold:

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

Capturar/liberar:

```bash
curl -s -X POST http://localhost:8082/holds/{hold_id}/capture
curl -s -X POST http://localhost:8082/holds/{hold_id}/release
```

## Evento Consumido

Topico: `ledger-posting-created`

Chave: `external_id`

Cada linha deve conter:

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

## Modelo de Dados

Tabelas principais:

- `account_balances`: saldo atual por `digital_account_id`.
- `account_balance_entries`: extrato, com `digital_account_id` e `ledger_account_id`.
- `account_holds`: holds por `digital_account_id`.
- `processed_ledger_entries`: idempotencia de eventos processados.

Colunas legadas `account_id` podem existir por compatibilidade de migration, mas nao devem ser usadas por APIs publicas.

## Configuracao

| Variavel | Padrao | Descricao |
| --- | --- | --- |
| `SERVER_PORT` | `8082` | Porta HTTP. |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/bank_flow` | JDBC URL. |
| `POSTGRES_USER` | `myuser` | Usuario Postgres. |
| `POSTGRES_PASSWORD` | `mysecretpassword` | Senha Postgres. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers. |
| `KAFKA_CONSUMER_GROUP_ID` | `bank-flow-balance` | Consumer group. |
| `KAFKA_AUTO_OFFSET_RESET` | `earliest` | Offset reset. |
| `KAFKA_RETRY_INTERVAL_MS` | `1000` | Intervalo entre retries. |
| `KAFKA_RETRY_MAX_ATTEMPTS` | `3` | Tentativas antes da DLT. |
| `KAFKA_HEALTH_TIMEOUT_MS` | `2000` | Timeout health Kafka. |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Export de traces para Tempo. |

## Observability

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
GET /actuator/metrics
GET /actuator/prometheus
```

Metricas customizadas do balance:

- `bank_flow_balance_kafka_messages_total`
- `bank_flow_balance_projection_total`
- `bank_flow_balance_projection_duration`
- `bank_flow_balance_projection_lines`

## Rodando

```bash
docker compose up -d db kafka kafka-init
cd bank-flow-balance
./gradlew bootRun
```

## Testes

```bash
cd bank-flow-balance
./gradlew test
```

## Comandos Uteis

```bash
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM account_balances LIMIT 10;"
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM account_holds ORDER BY created_at DESC LIMIT 10;"
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM account_balance_entries ORDER BY occurred_at DESC LIMIT 10;"
```

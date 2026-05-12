# bank-flow-balance

Servico responsavel por projetar saldos/extratos a partir do `ledger-posting-created` e por gerenciar holds de saldo usados pelo transfer-service.

O projeto agora e multi-modulo:

| Modulo | Runtime | Porta padrao | Responsabilidade |
| --- | --- | --- | --- |
| `:shared` | biblioteca | n/a | Dominio, services, repositorios, metricas e migrations. |
| `:api` | Spring Boot | `8082` | Endpoints HTTP de saldo, extrato e holds. |
| `:worker` | Spring Boot | `8087` | Consumer Kafka, projecao de ledger e expiracao agendada de holds. |

A API publica usa `digital_account_id`. O `ledger_account_id` numerico pode ser armazenado internamente em linhas de extrato, mas nao e identificador publico.

## Responsabilidades

- No worker, consumir `ledger-posting-created`.
- No worker, projetar `account_balances` por `digital_account_id`.
- Gravar linhas historicas em `account_balance_entries`.
- Garantir idempotencia por `entry_id` e `external_id`.
- Criar, capturar e liberar holds.
- Na API, expor saldo, extrato, health, métricas e traces.

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
  -> se ja CAPTURED, retorna sucesso idempotente

transfer POST /holds/{hold_id}/release
  -> muda HELD para RELEASED
  -> decrementa held_minor
  -> se ja RELEASED, retorna sucesso idempotente
```

## Regras de Negocio

- O balance projeta o resultado do ledger; ele nao decide partida contabil.
- `available_minor = posted_minor - held_minor`.
- `ledger-posting-created` e idempotente por `entry_id` e `external_id`.
- Criar hold exige saldo disponivel suficiente.
- Hold altera apenas `held_minor`; `posted_minor` muda somente pela projecao do ledger.
- Capturar ou liberar um hold reduz `held_minor` e fecha o hold.
- Repetir a mesma operacao terminal de hold e idempotente.
- Capturar hold `RELEASED` ou liberar hold `CAPTURED` e transicao invalida.

## Validacoes

- A chave Kafka de `ledger-posting-created` deve ser igual ao `external_id`.
- Evento de posting precisa ter `entry_id`, `external_id`, `entry_type`, `status`, `description`, `occurred_at`, `created_at` e ao menos duas linhas.
- `status` do posting deve ser `POSTED`.
- Linhas do posting precisam balancear para zero por moeda.
- Cada linha precisa ter `line_id`, `entry_id`, `account_id`, `digital_account_id`, `direction`, `amount_minor`, `signed_amount_minor`, `currency`, `line_memo` e `created_at`.
- `POST /holds` exige `transfer_id`, `digital_account_id`, `amount_minor`, `currency`, `reason` e `expires_at`.
- `amount_minor` deve ser positivo e `expires_at` deve estar no futuro.
- Consulta de extrato exige `limit` positivo e `cursor` valido quando informado.

Mais detalhes estao em [../docs/fluxos-regras-validacoes.md](../docs/fluxos-regras-validacoes.md).

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

Topico consumido pelo `:worker`: `ledger-posting-created`

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
| `KAFKA_CONSUMER_GROUP_ID` | `bank-flow-balance-worker` | Consumer group do worker. |
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
- `balance_projection_lag_seconds`
- `balance_available_minor`
- `balance_held_minor`
- `balance_holds_created_total`
- `balance_holds_captured_total`
- `balance_holds_released_total`
- `balance_holds_expired_total`
- `balance_hold_close_failures_total`

## Rodando

```bash
docker compose up -d db kafka kafka-init
cd bank-flow-balance
./gradlew :api:bootRun
./gradlew :worker:bootRun
```

## Build de Imagem

```bash
cd bank-flow-balance
./gradlew :api:bootBuildImage --imageName=bank-flow-balance-api:local
./gradlew :worker:bootBuildImage --imageName=bank-flow-balance-worker:local
```

## Kubernetes

O chart em `k8s/` cria deployments, services, ServiceMonitors e Probes separados:

- `bank-flow-balance-api`
- `bank-flow-balance-worker`

Deploy dos dois modulos:

```bash
helm upgrade --install bank-flow-balance bank-flow-balance/k8s
```

Deploy individual da API:

```bash
helm upgrade --install bank-flow-balance bank-flow-balance/k8s \
  --set components.worker.enabled=false
```

Deploy individual do Worker:

```bash
helm upgrade --install bank-flow-balance bank-flow-balance/k8s \
  --set components.api.enabled=false
```

## Testes

```bash
cd bank-flow-balance
./gradlew test
./gradlew :api:test
./gradlew :worker:test
```

Os testes usam PostgreSQL via Testcontainers para cobrir migrations com sintaxe PostgreSQL. Docker precisa estar disponivel no ambiente de CI/local.

## Comandos Uteis

```bash
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM account_balances LIMIT 10;"
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM account_holds ORDER BY created_at DESC LIMIT 10;"
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM account_balance_entries ORDER BY occurred_at DESC LIMIT 10;"
```

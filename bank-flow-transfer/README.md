# bank-flow-transfer

Servico responsavel por orquestrar transferencias entre contas digitais. Ele usa somente `digital_account_id` na API e nas integracoes com `accounts` e `balance`; o ledger resolve internamente o `account_id` contabil.

## Responsabilidades

- Receber `POST /transfers` com `Idempotency-Key`.
- Consultar `bank-flow-accounts` para validar contas e obter `branch/account`.
- Criar hold no `bank-flow-balance`.
- Chamar PSP e aguardar webhook.
- Em PSP `CONFIRMED`, publicar comando para o ledger via outbox.
- Receber transferencias de outras instituicoes via webhook inbound.
- Consumir `ledger-posting-created`, capturar hold e marcar `COMPLETED`.
- Em PSP `FAILED`, liberar hold e marcar `FAILED`.

## Fluxo

```text
POST /transfers
  -> valida source/destination digital_account_id
  -> busca contas no accounts-service
  -> cria transferencia RECEIVED
  -> cria hold no balance
  -> chama PSP
  -> aguarda webhook

POST /webhooks/psp/transfers CONFIRMED
  -> grava ledger.transfer_posted no outbox
  -> marca POSTING_REQUESTED
  -> outbox publica ledger-movements
  -> ledger publica ledger-posting-created
  -> transfer captura hold
  -> marca COMPLETED

POST /webhooks/external-institutions/transfers
  -> valida destination digital_account_id
  -> usa conta contabil de liquidacao como origem
  -> grava ledger.transfer_posted no outbox
  -> marca POSTING_REQUESTED
  -> ledger publica ledger-posting-created
  -> transfer marca COMPLETED
```

## Regras de Negocio

- `POST /transfers` exige `Idempotency-Key`.
- Repetir a mesma chave retorna a transferencia ja persistida.
- Conta origem e destino precisam existir no `accounts` e estar `ACTIVE`.
- Origem e destino nao podem ser o mesmo `digital_account_id`.
- Transferencia interna cria hold antes da chamada ao PSP.
- PSP `CONFIRMED` solicita postagem contabil; PSP `FAILED` libera o hold e marca a transferencia como `FAILED`.
- Transferencia inbound externa nao cria hold; ela usa a conta contabil de liquidacao como origem.
- A idempotencia do inbound externo usa `source_institution_code` + `external_transfer_id`.
- `COMPLETED`, `FAILED`, `EXPIRED` e `REVERSED` sao estados terminais.
- O outbox usa lock com `FOR UPDATE SKIP LOCKED`, `locked_by` e `locked_until`, permitindo mais de uma replica sem publicar o mesmo evento simultaneamente.

## Validacoes

- `source_digital_account_id`, `destination_digital_account_id`, `amount_minor`, `currency` e `description` sao obrigatorios em transferencia interna.
- `amount_minor` deve ser positivo.
- `currency` deve ter 3 letras; no contrato contabil com o ledger, deve ser BRL.
- Webhook PSP exige `psp_payment_id` e `status`; status aceitos: `CONFIRMED` e `FAILED`.
- Webhook inbound exige `source_institution_code`, `external_transfer_id`, `destination_digital_account_id`, `amount_minor`, `currency` e `description`.
- No inbound externo, `currency` deve ser `BRL`.
- O consumer de `ledger-posting-created` exige chave Kafka igual ao `external_id` do payload.

Mais detalhes estao em [../docs/fluxos-regras-validacoes.md](../docs/fluxos-regras-validacoes.md).

## API

Porta padrao: `8083`.

Criar transferencia:

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

Consultar:

```bash
curl -s http://localhost:8083/transfers/{transfer_id}
```

Webhook PSP:

```bash
curl -s -X POST http://localhost:8083/webhooks/psp/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "psp_payment_id": "psp-{transfer_id}",
    "status": "CONFIRMED",
    "failure_reason": null
  }'
```

Status aceitos no webhook: `CONFIRMED` e `FAILED`.

Webhook inbound de outras instituicoes:

```bash
curl -s -X POST http://localhost:8083/webhooks/external-institutions/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "source_institution_code": "260",
    "source_institution_name": "Instituicao Externa",
    "external_transfer_id": "evt-123",
    "destination_digital_account_id": "530743d7-9663-453f-9ef5-3c68ec4f7929",
    "amount_minor": 2500,
    "currency": "BRL",
    "description": "Transferencia recebida"
  }'
```

Idempotencia inbound: `source_institution_code` + `external_transfer_id`.

Conta contabil usada como origem no inbound externo:

```text
source_digital_account_id: 00000000-0000-0000-0000-000000000100
source_account: SETTLEMENT_EXTERNAL_INBOUND_BRL
```

## Status

- `RECEIVED`: transferencia registrada.
- `HOLD_CREATED`: saldo reservado.
- `PSP_PENDING`: PSP pendente.
- `PSP_CONFIRMED`: confirmacao recebida.
- `POSTING_REQUESTED`: comando contabil registrado no outbox.
- `COMPLETED`: ledger postou e hold foi capturado.
- `FAILED`: falha antes da postagem contabil.
- `EXPIRED`: janela operacional da transferencia expirou antes da conclusao.
- `REVERSED`: transferencia concluida foi revertida por um fluxo de estorno.

## Kafka e Outbox

Topico publicado: `ledger-movements`

Chave: `source_digital_account_id`

No inbound externo, a chave e a origem usam a conta de liquidacao `00000000-0000-0000-0000-000000000100`.

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

Topico consumido: `ledger-posting-created`, chave `external_id`.

## Configuracao

| Variavel | Padrao | Descricao |
| --- | --- | --- |
| `SERVER_PORT` | `8083` | Porta HTTP. |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/bank_flow?currentSchema=transfer,public` | JDBC URL. |
| `BALANCE_BASE_URL` | `http://localhost:8082` | URL do balance. |
| `ACCOUNTS_BASE_URL` | `http://localhost:8084` | URL do accounts. |
| `PSP_MODE` | `mock` | Modo do PSP. |
| `PSP_BASE_URL` | `http://localhost:9099` | URL do PSP externo. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers. |
| `OUTBOX_PUBLISHER_FIXED_DELAY_MS` | `1000` | Intervalo do publisher. |
| `OUTBOX_PUBLISHER_BATCH_SIZE` | `50` | Tamanho do lote. |
| `OUTBOX_PUBLISHER_LOCK_LEASE_MS` | `60000` | Tempo de posse do lock de evento em processamento. |
| `OUTBOX_PUBLISHER_SEND_TIMEOUT_MS` | `30000` | Timeout para publish Kafka. |
| `OUTBOX_PUBLISHER_MAX_ATTEMPTS` | `10` | Tentativas antes de marcar evento como `FAILED`. |
| `KAFKA_PRODUCER_MAX_BLOCK_MS` | `30000` | Timeout maximo de bloqueio do producer Kafka. |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Export de traces para Tempo. |

## Observability

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

O servico emite métricas HTTP/JVM/Hikari/Kafka, logs estruturados e traces OpenTelemetry.

Metricas de negocio:

- `transfers_created_total`
- `transfers_completed_total`
- `transfers_failed_total{reason}`
- `transfer_psp_confirmed_total`
- `transfer_end_to_end_latency_seconds`
- `transfers_in_status{status}`
- `transfer_oldest_in_status_age_seconds{status}`
- `outbox_pending_events{service="bank-flow-transfer"}`
- `outbox_oldest_pending_event_age_seconds{service="bank-flow-transfer"}`
- `outbox_publish_failures_total{service,topic,event_type}`

## Rodando

```bash
docker compose up -d db kafka kafka-init
cd bank-flow-transfer
./gradlew bootRun
```

Suba tambem `accounts`, `balance` e `ledger` para o fluxo completo.

## Testes

```bash
cd bank-flow-transfer
./gradlew test
```

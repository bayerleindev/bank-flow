# bank-flow-transfer

Servico responsavel por orquestrar transferencias entre contas digitais. Ele usa somente `digital_account_id` na API e nas integracoes com `accounts` e `balance`; o ledger resolve internamente o `account_id` contabil.

## Responsabilidades

- Receber `POST /transfers` com `Idempotency-Key`.
- Consultar `bank-flow-accounts` para validar contas e obter `branch/account`.
- Criar hold no `bank-flow-balance`.
- Chamar PSP e aguardar webhook.
- Em PSP `CONFIRMED`, publicar comando para o ledger via outbox.
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
```

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

## Status

- `RECEIVED`: transferencia registrada.
- `HOLD_CREATED`: saldo reservado.
- `PSP_PENDING`: PSP pendente.
- `PSP_CONFIRMED`: confirmacao recebida.
- `POSTING_REQUESTED`: comando contabil registrado no outbox.
- `COMPLETED`: ledger postou e hold foi capturado.
- `FAILED`: falha antes da postagem contabil.

## Kafka e Outbox

Topico publicado: `ledger-movements`

Chave: `source_digital_account_id`

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
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Export de traces para Tempo. |

## Observability

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

O servico emite métricas HTTP/JVM/Hikari/Kafka, logs estruturados e traces OpenTelemetry.

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

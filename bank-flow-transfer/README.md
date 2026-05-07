# bank-flow-transfer

Servico responsavel por orquestrar transferencias entre contas. Ele cria holds no `bank-flow-balance`, aciona o PSP, publica comandos para o `bank-flow-ledger` via outbox e conclui a transferencia apos receber `ledger-posting-created`.

## Responsabilidades

- Receber criacao de transferencias HTTP com idempotencia por `Idempotency-Key`.
- Criar hold no balance-service antes de chamar o PSP.
- Registrar `psp_payment_id` e acompanhar webhooks do PSP.
- Em confirmacao PSP, gravar comando `ledger.transfer_posted` no outbox.
- Publicar outbox em `ledger-movements`.
- Consumir `ledger-posting-created` e completar transferencias `POSTING_REQUESTED`.
- Capturar hold em sucesso contabil ou liberar hold em falha PSP.

## Fluxo Principal

```text
POST /transfers
  ├── cria transferencia RECEIVED
  ├── POST balance /holds
  ├── chama PSP
  └── status PSP_PENDING ou PSP_CONFIRMED

POST /webhooks/psp/transfers CONFIRMED
  ├── grava outbox ledger.transfer_posted
  └── marca POSTING_REQUESTED

OutboxPublisher
  └── publica ledger-movements

ledger-posting-created
  ├── captura hold no balance
  └── marca COMPLETED
```

## API HTTP

Porta padrao: `8083`.

Criar transferencia:

```bash
curl -s -X POST http://localhost:8083/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: transfer-001" \
  -d '{
    "source_account_id": 1001,
    "source_owner_id": "018f6e4f-f427-7c32-9d4b-3bc9e72872b1",
    "source_account": "12345-6",
    "destination_account_id": 2002,
    "destination_owner_id": "018f6e4f-f427-7c32-9d4b-3bc9e72872b2",
    "destination_account": "98765-4",
    "amount_minor": 1500,
    "currency": "BRL",
    "description": "Transferencia teste"
  }'
```

Consultar transferencia:

```bash
curl -s http://localhost:8083/transfers/{transfer_id}
```

Webhook PSP:

```bash
curl -s -X POST http://localhost:8083/webhooks/psp/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "psp_payment_id": "psp-123",
    "status": "CONFIRMED"
  }'
```

## Status da Transferencia

- `RECEIVED`: transferencia registrada.
- `HOLD_CREATED`: saldo reservado no balance.
- `PSP_PENDING`: pagamento aguardando confirmacao.
- `PSP_CONFIRMED`: confirmacao PSP recebida.
- `POSTING_REQUESTED`: comando para ledger registrado no outbox.
- `COMPLETED`: posting recebido do ledger e hold capturado.
- `FAILED`: PSP falhou ou houve erro antes da postagem.

## Outbox e Kafka

Tabela:

```text
outbox_events
```

O outbox publica comandos no topico `ledger-movements` com chave `source_owner_id`. O payload segue o contrato do ledger:

```json
{
  "transfer_id": "018f6e4f-f427-7c32-9d4b-3bc9e72872bf",
  "source_owner_id": "018f6e4f-f427-7c32-9d4b-3bc9e72872b1",
  "source_account": "12345-6",
  "destination_owner_id": "018f6e4f-f427-7c32-9d4b-3bc9e72872b2",
  "destination_account": "98765-4",
  "amount_cents": 1500,
  "currency": "BRL"
}
```

O consumidor de `ledger-posting-created` processa apenas eventos `entry_type=TRANSFER` e exige chave Kafka igual ao `external_id`.

## Configuracao

| Variavel | Padrao | Descricao |
| --- | --- | --- |
| `SERVER_PORT` | `8083` | Porta HTTP. |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/bank_flow?currentSchema=transfer,public` | JDBC URL. |
| `POSTGRES_USER` | `myuser` | Usuario do Postgres. |
| `POSTGRES_PASSWORD` | `mysecretpassword` | Senha do Postgres. |
| `BALANCE_BASE_URL` | `http://localhost:8082` | URL do balance-service. |
| `PSP_MODE` | `mock` | Modo do PSP. |
| `PSP_BASE_URL` | `http://localhost:9099` | URL do PSP externo. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Bootstrap servers Kafka. |
| `KAFKA_CONSUMER_GROUP_ID` | `bank-flow-transfer` | Consumer group. |
| `OUTBOX_PUBLISHER_FIXED_DELAY_MS` | `1000` | Intervalo do publisher do outbox. |
| `OUTBOX_PUBLISHER_BATCH_SIZE` | `50` | Tamanho do lote do outbox. |

## Rodando Localmente

Suba dependencias:

```bash
docker compose up -d db kafka kafka-init
```

Inicie balance, ledger e depois transfer:

```bash
cd bank-flow-transfer
./gradlew bootRun
```

## Testes

```bash
cd bank-flow-transfer
./gradlew test
```

Os testes cobrem idempotencia de criacao, hold, falha PSP, confirmacao PSP com outbox e conclusao apos `ledger-posting-created`.

## Comandos Uteis

Consultar transferencias:

```bash
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM transfer.transfers ORDER BY created_at DESC LIMIT 10;"
```

Consultar outbox:

```bash
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM transfer.outbox_events ORDER BY created_at DESC LIMIT 10;"
```

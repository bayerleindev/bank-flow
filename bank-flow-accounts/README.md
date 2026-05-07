# bank-flow-accounts

Servico responsavel por criar contas digitais. Ele valida dados cadastrais, chama um BaaS, salva `branch`/`account` retornados e publica `account-created` para o ledger.

Este servico expõe e persiste o identificador operacional `digital_account_id`. O `account_id` numerico contabil e criado e usado apenas pelo `bank-flow-ledger`.

## Responsabilidades

- Receber `POST /accounts` com `Idempotency-Key`.
- Validar CPF, email, telefone e data de nascimento.
- Chamar BaaS em modo `mock` ou `http`.
- Persistir `baas_account_id`, `branch`, `account`, `currency` e status.
- Publicar `account-created` via outbox quando a conta fica `ACTIVE`.
- Consultar conta por `GET /accounts/{digital_account_id}`.

## Fluxo

```text
POST /accounts
  -> valida payload
  -> cria conta RECEIVED
  -> chama BaaS
  -> salva branch/account
  -> marca ACTIVE, BAAS_PENDING ou REJECTED
  -> se ACTIVE, grava account.created no outbox
  -> OutboxPublisher publica account-created no Kafka
```

## API

Porta padrao: `8084`.

Criar conta:

```bash
curl -s -X POST http://localhost:8084/accounts \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: account-001" \
  -d '{
    "fullName": "Maria Silva",
    "documentNumber": "35225454860",
    "email": "maria@example.com",
    "motherName": "Ana Silva",
    "socialName": "Maria",
    "phoneNumber": "+5511999999999",
    "birthDate": "18-12-1996",
    "address": "Rua Teste, 123",
    "isPoliticallyExposed": false
  }'
```

Consultar:

```bash
curl -s http://localhost:8084/accounts/{digital_account_id}
```

Resposta:

```json
{
  "digital_account_id": "530743d7-9663-453f-9ef5-3c68ec4f7929",
  "document_number": "***4860",
  "email": "maria@example.com",
  "baas_account_id": "baas-35225454860",
  "branch": "0001",
  "account": "54860-0",
  "currency": "BRL",
  "status": "ACTIVE",
  "failure_reason": null,
  "created_at": 1778101782961,
  "updated_at": 1778101782961
}
```

## Evento Publicado

Topico: `account-created`

Chave Kafka: `digital_account_id`

Payload:

```json
{
  "digital_account_id": "530743d7-9663-453f-9ef5-3c68ec4f7929",
  "branch": "0001",
  "account": "54860-0",
  "currency": "BRL"
}
```

## Configuracao

| Variavel | Padrao | Descricao |
| --- | --- | --- |
| `SERVER_PORT` | `8084` | Porta HTTP. |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/bank_flow?currentSchema=accounts,public` | JDBC URL. |
| `POSTGRES_USER` | `myuser` | Usuario Postgres. |
| `POSTGRES_PASSWORD` | `mysecretpassword` | Senha Postgres. |
| `BAAS_MODE` | `mock` | Modo do BaaS. |
| `BAAS_BASE_URL` | `http://localhost:9098` | URL base do BaaS HTTP. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers. |
| `OUTBOX_PUBLISHER_FIXED_DELAY_MS` | `1000` | Intervalo do publisher. |
| `OUTBOX_PUBLISHER_BATCH_SIZE` | `50` | Tamanho do lote. |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Export de traces para Tempo. |

## Observability

Endpoints:

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

O servico emite métricas Prometheus, logs estruturados e traces OpenTelemetry.

Metricas de negocio:

- `accounts_created_total`
- `accounts_in_status{status}`
- `account_oldest_in_status_age_seconds{status}`
- `outbox_pending_events{service="bank-flow-accounts"}`
- `outbox_oldest_pending_event_age_seconds{service="bank-flow-accounts"}`
- `outbox_publish_failures_total{service,topic,event_type}`

## Rodando

```bash
docker compose up -d db kafka kafka-init
cd bank-flow-accounts
./gradlew bootRun
```

## Testes

```bash
cd bank-flow-accounts
./gradlew test
```

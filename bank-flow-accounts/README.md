# bank-flow-accounts

Servico responsavel por criar contas digitais no Bank Flow. Ele recebe dados cadastrais, chama um BaaS para efetivar a conta, salva `branch` e `account` retornados pelo BaaS e publica `account-created` via outbox para o ledger criar a conta contabil.

## Responsabilidades

- Receber `POST /accounts` com `Idempotency-Key`.
- Validar dados cadastrais, incluindo CPF e data de nascimento.
- Chamar o BaaS para abertura efetiva da conta.
- Persistir `baas_account_id`, `branch`, `account`, `currency` e status.
- Publicar evento `account-created` no Kafka via outbox.
- Permitir consulta por `GET /accounts/{account_id}`.

## Fluxo Principal

```text
POST /accounts
  ├── valida payload
  ├── cria account RECEIVED
  ├── chama BaaS
  ├── salva branch/account retornados pelo BaaS
  ├── marca ACTIVE, BAAS_PENDING ou REJECTED
  └── se ACTIVE, grava account.created no outbox

OutboxPublisher
  └── publica account-created
        └── bank-flow-ledger cria ledger_account
```

## API HTTP

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

Consultar conta:

```bash
curl -s http://localhost:8084/accounts/{account_id}
```

Resposta:

```json
{
  "account_id": "018f6e4f-f427-7c32-9d4b-3bc9e72872bf",
  "owner_id": "018f6e4f-f427-7c32-9d4b-3bc9e72872b1",
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

## Status da Conta

- `RECEIVED`: conta registrada localmente antes da chamada BaaS.
- `BAAS_PENDING`: BaaS aceitou, mas ainda nao ativou a conta.
- `ACTIVE`: BaaS ativou a conta; evento `account-created` e publicado.
- `REJECTED`: BaaS recusou a abertura.
- `FAILED`: reservado para falhas operacionais futuras.

## BaaS

O BaaS deve retornar pelo menos:

```json
{
  "baas_account_id": "baas-123",
  "branch": "0001",
  "account": "12345-6",
  "currency": "BRL",
  "status": "ACTIVE",
  "failure_reason": null
}
```

Modos:

- `BAAS_MODE=mock`: retorna uma conta fake deterministica.
- `BAAS_MODE=http`: chama `POST {BAAS_BASE_URL}/accounts`.

O client HTTP repassa `Idempotency-Key` para o BaaS.

## Outbox e Kafka

Quando a conta fica `ACTIVE`, o servico grava um evento outbox:

```json
{
  "owner_id": "018f6e4f-f427-7c32-9d4b-3bc9e72872b1",
  "branch": "0001",
  "account": "12345-6",
  "currency": "BRL"
}
```

Topico: `account-created`

Chave Kafka: `owner_id`

Esse contrato ja e consumido pelo `bank-flow-ledger`.

## Configuracao

| Variavel | Padrao | Descricao |
| --- | --- | --- |
| `SERVER_PORT` | `8084` | Porta HTTP. |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/bank_flow?currentSchema=accounts,public` | JDBC URL. |
| `POSTGRES_USER` | `myuser` | Usuario do Postgres. |
| `POSTGRES_PASSWORD` | `mysecretpassword` | Senha do Postgres. |
| `BAAS_MODE` | `mock` | Modo do client BaaS. |
| `BAAS_BASE_URL` | `http://localhost:9098` | URL base do BaaS HTTP. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Bootstrap servers Kafka. |
| `OUTBOX_PUBLISHER_FIXED_DELAY_MS` | `1000` | Intervalo do publisher do outbox. |
| `OUTBOX_PUBLISHER_BATCH_SIZE` | `50` | Tamanho do lote do outbox. |

## Rodando Localmente

Suba dependencias:

```bash
docker compose up -d db kafka kafka-init
```

Inicie o servico:

```bash
cd bank-flow-accounts
./gradlew bootRun
```

## Testes

```bash
cd bank-flow-accounts
./gradlew test
```

Os testes cobrem validacao do fluxo, idempotencia por chave/documento, retorno BaaS com `branch`/`account` e criacao do outbox.

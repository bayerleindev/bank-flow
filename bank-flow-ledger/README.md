# bank-flow-ledger

Servico responsavel por manter o livro contabil double-entry do Bank Flow. Ele consome comandos de conta, transferencia e estorno, persiste postings no immudb e publica eventos `ledger-posting-created` para read models como o `bank-flow-balance` e para orquestradores como o `bank-flow-transfer`.

## Responsabilidades

- Criar contas contabeis a partir de eventos `account-created` publicados pelo `bank-flow-accounts`.
- Processar comandos `ledger-movements` gerados pelo transfer-service.
- Gerar postings double-entry balanceados.
- Persistir postings de forma idempotente por `external_id`.
- Processar estornos por `ledger-reversals`.
- Publicar `ledger-posting-created` apos persistencia bem-sucedida.

## Arquitetura

Pacotes principais:

```text
br.com.bankflow.ledger
├── configs       # Kafka, Jackson, Clock, immudb
├── consumers     # consumidores Kafka
├── domain        # eventos, contas, entries e lines
├── repositories  # persistencia no immudb
└── services      # criacao de contas, movimentos e estornos
```

Fluxo de transferencia:

```text
bank-flow-transfer outbox
  └── ledger-movements
        └── bank-flow-ledger
              ├── valida chave source_owner_id
              ├── resolve contas contabeis por owner_id
              ├── cria debit/credit lines
              ├── persiste posting no immudb
              └── publica ledger-posting-created
```

## Topicos Kafka

Consumidos:

- `account-created`, chave `owner_id`, publicado pelo `bank-flow-accounts`.
- `ledger-movements`, chave `source_owner_id`.
- `ledger-reversals`, chave `original_external_id`.

Publicado:

- `ledger-posting-created`, chave `external_id`.

DLTs esperadas:

- `account-created.DLT`
- `ledger-movements.DLT`
- `ledger-reversals.DLT`

## Contratos Principais

Comando de transferencia em `ledger-movements`:

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

Evento publicado em `ledger-posting-created`:

```json
{
  "entry_id": 1778098838981000000,
  "external_id": "018f6e4f-f427-7c32-9d4b-3bc9e72872bf",
  "entry_type": "TRANSFER",
  "status": "POSTED",
  "description": "Transferencia BRL de conta 12345-6 para conta 98765-4",
  "occurred_at": 1778098838836,
  "created_at": 1778098838836,
  "reversal_of_entry_id": 0,
  "metadata": "{}",
  "lines": []
}
```

As linhas reais sao publicadas em `lines` com `DEBIT` e `CREDIT`, valores em minor units e soma zero por moeda.

## Configuracao

Propriedades e variaveis principais:

| Nome | Padrao | Descricao |
| --- | --- | --- |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Bootstrap servers do Kafka. |
| `spring.kafka.consumer.group-id` | `bank-flow-ledger` | Consumer group do servico. |
| `KAFKA_AUTO_OFFSET_RESET` | `latest` | Offset reset do consumer. |
| `ID_GENERATOR_WORKER_ID` | `0` | Worker id do gerador numerico. |
| `IMMUDB_ENABLED` | `true` | Liga persistencia real no immudb. |
| `IMMUDB_HOST` | `localhost` | Host do immudb. |
| `IMMUDB_PORT` | `3322` | Porta gRPC do immudb. |
| `IMMUDB_DATABASE` | `ledger` | Database usada pelo ledger. |
| `IMMUDB_USERNAME` | `immudb` | Usuario do immudb. |
| `IMMUDB_PASSWORD` | `immudb` | Senha do immudb. |

## Rodando Localmente

Suba a infraestrutura:

```bash
docker compose up -d kafka kafka-init immudb
```

Prepare as tabelas do immudb quando necessario:

```text
scripts/immudb/001_create_ledger_tables.sql
```

Esse SQL deve ser aplicado no database configurado em `IMMUDB_DATABASE` antes de processar eventos com `IMMUDB_ENABLED=true`.

Inicie o servico:

```bash
cd bank-flow-ledger
./gradlew bootRun
```

## Testes

```bash
cd bank-flow-ledger
./gradlew test
```

Os testes cobrem regras de dominio, geracao de IDs, criacao de postings, idempotencia por `external_id`, estornos e persistencia no immudb.

## Comandos Uteis

Produzir eventos de conta:

```bash
python3 scripts/produce_account_created.py
```

Produzir comandos de movimento:

```bash
python3 scripts/produce_ledger_movements.py
```

Listar topicos:

```bash
docker compose exec -T kafka kafka-topics --bootstrap-server kafka:29092 --list
```

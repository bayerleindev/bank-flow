# Bank Flow Backend

Este diretorio contem tres servicos Spring Boot que implementam o fluxo de transferencias, ledger contabil e projecao de saldo do Bank Flow.

## Servicos

### bank-flow-transfer

Orquestra transferencias entre contas. Ele recebe chamadas HTTP, cria holds no balance, chama o PSP, publica comandos para o ledger via outbox e conclui a transferencia quando recebe a confirmacao contabil.

Responsabilidades principais:

- `POST /transfers`: cria transferencia com `Idempotency-Key`.
- `POST /webhooks/psp/transfers`: recebe confirmacao ou falha do PSP.
- Publica comandos no topico `ledger-movements` usando a tabela `outbox_events`.
- Consome `ledger-posting-created` para capturar hold e marcar `COMPLETED`.

Leia mais em [bank-flow-transfer/README.md](bank-flow-transfer/README.md).

### bank-flow-ledger

Mantem o livro contabil double-entry. Ele consome comandos de criacao de conta, movimentos e estornos, persiste postings no immudb e publica eventos de postings criados.

Responsabilidades principais:

- Consome `account-created` para criar contas contabeis.
- Consome `ledger-movements` para postar transferencias.
- Consome `ledger-reversals` para criar estornos.
- Publica `ledger-posting-created` apos persistir o posting.

Leia mais em [bank-flow-ledger/README.md](bank-flow-ledger/README.md).

### bank-flow-balance

Materializa saldos, extratos e holds em PostgreSQL. Ele e um read model do ledger e tambem oferece APIs transacionais para reservar, capturar ou liberar saldo.

Responsabilidades principais:

- Consome `ledger-posting-created`.
- Atualiza `account_balances` e `account_balance_entries`.
- Expoe `GET /balances/{account_id}` e `GET /balances/{account_id}/statement`.
- Expoe `POST /holds`, `POST /holds/{hold_id}/capture` e `POST /holds/{hold_id}/release`.

Leia mais em [bank-flow-balance/README.md](bank-flow-balance/README.md).

## Como os Servicos Interagem

Fluxo de transferencia bem-sucedida:

```text
client
  └── POST /transfers
        └── bank-flow-transfer
              ├── cria transferencia RECEIVED
              ├── cria hold no bank-flow-balance
              ├── chama PSP
              └── aguarda webhook PSP

PSP
  └── POST /webhooks/psp/transfers CONFIRMED
        └── bank-flow-transfer
              ├── grava comando no outbox
              └── marca POSTING_REQUESTED

bank-flow-transfer outbox
  └── publica ledger-movements
        └── bank-flow-ledger
              ├── cria posting double-entry
              ├── persiste no immudb
              └── publica ledger-posting-created

ledger-posting-created
  ├── bank-flow-balance atualiza saldo/extrato
  └── bank-flow-transfer captura hold e marca COMPLETED
```

Fluxo com falha PSP:

```text
PSP FAILED
  └── bank-flow-transfer
        ├── libera hold no bank-flow-balance
        └── marca transferencia FAILED
```

## Topicos Kafka

| Topico | Produtor | Consumidor | Chave |
| --- | --- | --- | --- |
| `account-created` | scripts/outros sistemas | `bank-flow-ledger` | `owner_id` |
| `ledger-movements` | `bank-flow-transfer` | `bank-flow-ledger` | `source_owner_id` |
| `ledger-reversals` | scripts/outros sistemas | `bank-flow-ledger` | `original_external_id` |
| `ledger-posting-created` | `bank-flow-ledger` | `bank-flow-balance`, `bank-flow-transfer` | `external_id` |

Cada topico possui uma DLT correspondente com sufixo `.DLT`.

## Infra Local

O arquivo [docker-compose.yaml](docker-compose.yaml) sobe:

- PostgreSQL em `localhost:5432`.
- Kafka em `localhost:9092`.
- Kafka UI em `http://localhost:8081`.
- immudb em `localhost:3322`.

Subir dependencias:

```bash
docker compose up -d db kafka kafka-init kafka-ui immudb
```

## Rodando os Servicos

Em terminais separados:

```bash
cd bank-flow-balance
./gradlew bootRun
```

```bash
cd bank-flow-ledger
./gradlew bootRun
```

```bash
cd bank-flow-transfer
./gradlew bootRun
```

Portas padrao:

| Servico | Porta |
| --- | --- |
| `bank-flow-balance` | `8082` |
| `bank-flow-transfer` | `8083` |
| `bank-flow-ledger` | sem API HTTP publica |

## Testes

Rodar testes por modulo:

```bash
cd bank-flow-balance && ./gradlew test
cd ../bank-flow-ledger && ./gradlew test
cd ../bank-flow-transfer && ./gradlew test
```

## Scripts Uteis

Produzir eventos de conta:

```bash
python3 scripts/produce_account_created.py
```

Produzir movimentos direto no ledger:

```bash
python3 scripts/produce_ledger_movements.py
```

Produzir estornos:

```bash
python3 scripts/produce_ledger_reversals.py
```

## Bancos e Schemas

PostgreSQL usa o database `bank_flow`.

- `bank-flow-balance` cria tabelas no schema configurado pelo modulo de balance.
- `bank-flow-transfer` usa o schema `transfer`.
- `bank-flow-ledger` usa immudb para `ledger_accounts`, `ledger_entries` e `ledger_entry_lines`.

## Observacoes de Operacao

- O transfer usa outbox para evitar perder comandos ao ledger depois da confirmacao PSP.
- O ledger e a fonte de verdade contabil.
- O balance e uma projecao reconstruivel a partir de `ledger-posting-created`.
- O fechamento de hold acontece somente depois do resultado final: release em falha PSP, capture apos posting contabil.

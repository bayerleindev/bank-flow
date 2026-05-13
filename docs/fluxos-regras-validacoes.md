# Fluxos, Regras de Negocio e Validacoes

Esta documentacao descreve o comportamento funcional do Bank Flow. Ela complementa os READMEs dos servicos e deve ser usada como referencia para entender o que o sistema aceita, rejeita, publica e persiste.

## Convencoes Globais

- `digital_account_id` e o identificador operacional usado por APIs publicas e integracoes entre `accounts`, `transfer` e `balance`.
- `account_id` e um identificador contabil numerico interno, criado e usado apenas pelo `ledger`.
- Valores monetarios trafegam em unidade minoritaria (`amount_minor` ou `amount_cents`), sem ponto decimal.
- Transferencias e ledger suportam BRL nos fluxos principais implementados.
- Kafka trabalha com entrega pelo menos uma vez. Por isso, producers usam outbox e consumers validam chaves e aplicam idempotencia.
- Eventos invalidos por contrato geram erro nao retryable nos consumers e seguem para DLT apos tratamento do Spring Kafka.

## Fluxo de Criacao de Conta

```text
POST /accounts
  -> accounts valida Idempotency-Key e payload cadastral
  -> cria registro local RECEIVED
  -> chama BaaS mock/http
  -> se BaaS retorna ACTIVE, salva branch/account/currency/status
  -> grava account.created no outbox
  -> OutboxPublisher publica account-created
  -> ledger consome account-created
  -> ledger cria ledger account CUSTOMER_ACCOUNT_<digital_account_id> no immudb
```

Regras de negocio:

- Toda criacao exige `Idempotency-Key`.
- Repetir a mesma chave retorna o resultado ja persistido, sem criar nova conta.
- Apenas conta `ACTIVE` gera evento `account-created`.
- O BaaS e a fonte de `baas_account_id`, `branch`, `account`, `currency` e status operacional.
- `accounts` nao cria nem conhece `account_id` contabil.

Validacoes:

- `fullName`, `motherName`, `phoneNumber`, `birthDate` e `address` sao obrigatorios.
- `documentNumber` deve ser CPF valido.
- `email` deve ter formato valido.
- O evento `account-created` precisa ter `digital_account_id`, `branch`, `account` e `currency`.
- No ledger, a chave Kafka de `account-created` deve ser igual ao `digital_account_id` do payload.

## Fluxo de Transferencia Entre Contas Internas

```text
POST /transfers
  -> transfer valida Idempotency-Key e payload
  -> consulta source e destination no accounts
  -> exige contas ACTIVE
  -> cria transferencia RECEIVED
  -> cria hold no balance para a conta source
  -> chama PSP
  -> marca PSP_PENDING

POST /webhooks/psp/transfers CONFIRMED
  -> localiza transferencia por psp_payment_id
  -> marca PSP_CONFIRMED
  -> grava ledger.transfer_posted no outbox
  -> marca POSTING_REQUESTED
  -> OutboxPublisher publica ledger-movements
  -> ledger cria posting double-entry
  -> ledger publica ledger-posting-created
  -> balance projeta saldo/extrato
  -> transfer consome ledger-posting-created
  -> captura hold
  -> marca COMPLETED
```

Fluxo de falha no PSP:

```text
POST /webhooks/psp/transfers FAILED
  -> localiza transferencia por psp_payment_id
  -> libera hold no balance, se existir
  -> marca FAILED
```

Regras de negocio:

- Toda transferencia interna exige `Idempotency-Key`.
- Repetir a mesma chave retorna a transferencia ja persistida.
- Conta origem e destino devem existir e estar `ACTIVE`.
- Origem e destino nao podem ser o mesmo `digital_account_id`.
- O saldo e reservado via hold antes da chamada ao PSP.
- A postagem contabil so e solicitada apos PSP `CONFIRMED`.
- `COMPLETED`, `FAILED`, `EXPIRED` e `REVERSED` sao estados terminais.
- Uma transferencia terminal nao deve ser reprocessada para outro estado operacional.

Validacoes:

- `source_digital_account_id`, `destination_digital_account_id`, `amount_minor`, `currency` e `description` sao obrigatorios.
- `amount_minor` deve ser positivo.
- `currency` deve ter 3 letras; no contrato com ledger, a transferencia deve ser BRL.
- O webhook PSP exige `psp_payment_id` e `status`.
- Status aceitos no webhook PSP: `CONFIRMED` e `FAILED`.
- No consumer do transfer, a chave Kafka de `ledger-posting-created` deve ser igual ao `external_id` do payload.

## Fluxo de Transferencia Inbound Externa

```text
POST /webhooks/external-institutions/transfers
  -> transfer monta idempotency_key tecnica
  -> valida destino no accounts
  -> usa conta de liquidacao como origem
  -> cria transferencia ja confirmada para contabilizacao
  -> grava ledger.transfer_posted no outbox
  -> marca POSTING_REQUESTED
  -> OutboxPublisher publica ledger-movements
  -> ledger debita liquidacao e credita conta destino
  -> ledger publica ledger-posting-created
  -> balance projeta saldo/extrato
  -> transfer marca COMPLETED ao receber ledger-posting-created
```

Conta de liquidacao usada como origem:

```text
source_digital_account_id: 00000000-0000-0000-0000-000000000100
source_account: SETTLEMENT_EXTERNAL_INBOUND_BRL
```

Regras de negocio:

- A idempotencia do inbound externo e derivada de `source_institution_code` + `external_transfer_id`.
- O inbound externo nao cria hold, porque o dinheiro vem de outra instituicao.
- A conta destino precisa existir e estar `ACTIVE`.
- A conta contabil `SETTLEMENT_EXTERNAL_INBOUND_BRL` precisa existir no immudb antes do primeiro evento.
- O seed da conta de liquidacao fica em `scripts/immudb/002_seed_settlement_accounts.sql`.

Validacoes:

- `source_institution_code`, `external_transfer_id`, `destination_digital_account_id`, `amount_minor`, `currency` e `description` sao obrigatorios.
- `amount_minor` deve ser positivo.
- `currency` deve ser exatamente `BRL`.
- O `ledger-movements` publicado usa a chave Kafka da conta de liquidacao.

## Fluxo Contabil no Ledger

```text
ledger-movements
  -> valida chave source_digital_account_id
  -> valida payload de transferencia
  -> resolve ledger account origem e destino
  -> monta uma linha DEBIT e uma linha CREDIT
  -> valida partida dobrada
  -> persiste entry/lines no immudb
  -> publica ledger-posting-created
```

Regras de negocio:

- O ledger e o unico servico que cria e usa `account_id`.
- Cada posting precisa ter pelo menos duas linhas.
- A soma das linhas assinadas precisa fechar em zero por moeda.
- Linha `DEBIT` deve ter `signed_amount_minor` negativo.
- Linha `CREDIT` deve ter `signed_amount_minor` positivo.
- `external_id` e a chave de idempotencia do posting contabil.
- Se um `ledger-movements` duplicado chegar depois de o posting ja existir, o ledger republica o `ledger-posting-created` existente para reparar falha entre persistencia e publish.

Validacoes:

- No `ledger-movements`, a chave Kafka deve ser igual ao `source_digital_account_id`.
- `transfer_id`, `source_digital_account_id`, `source_account`, `destination_digital_account_id`, `destination_account`, `amount_cents` e `currency` sao obrigatorios.
- `amount_cents` deve ser positivo.
- `currency` deve ser `BRL`.
- Contas contabeis de origem e destino precisam existir.
- `LedgerPosting` rejeita postings sem linhas suficientes, com moedas misturadas, directions invalidas, sinais incorretos ou soma diferente de zero.

## Fluxo de Estorno Contabil

```text
ledger-reversals
  -> valida chave original_external_id
  -> busca posting original
  -> impede reversao de reversao
  -> impede reversao de posting ja revertido
  -> cria linhas invertidas
  -> persiste posting de reversao
  -> publica ledger-posting-created
```

Regras de negocio:

- A chave idempotente do posting de reversao e deterministica: `reversal:<original_external_id>`.
- Diferentes mensagens pedindo estorno do mesmo `original_external_id` convergem para a mesma reversao.
- Uma reversao nao pode ser revertida novamente.
- Um posting original ja revertido nao pode receber segunda reversao contabil.

Validacoes:

- No `ledger-reversals`, a chave Kafka deve ser igual ao `original_external_id`.
- `reversal_id`, `original_external_id` e `reason` sao obrigatorios.
- O posting original deve existir.
- O posting original nao pode ser uma reversao.
- O posting original nao pode estar marcado como ja revertido.

## Fluxo de Projecao de Saldo

```text
ledger-posting-created
  -> balance valida chave external_id
  -> valida payload e linhas
  -> registra processed_ledger_entries
  -> se ja processado, ignora com sucesso
  -> grava account_balance_entries
  -> atualiza posted_minor em account_balances
```

Regras de negocio:

- O balance nao calcula contabilidade; ele projeta o resultado do ledger.
- A idempotencia usa `entry_id` e `external_id` em `processed_ledger_entries`.
- Cada linha do posting vira uma linha de extrato para o respectivo `digital_account_id`.
- `available_minor = posted_minor - held_minor`.

Validacoes:

- A chave Kafka de `ledger-posting-created` deve ser igual ao `external_id`.
- `entry_id`, `external_id`, `entry_type`, `status`, `description`, `occurred_at`, `created_at` e `lines` sao obrigatorios.
- `status` deve ser `POSTED`.
- O evento precisa ter pelo menos duas linhas.
- As linhas precisam balancear para zero por moeda.
- Cada linha precisa ter `line_id`, `entry_id`, `account_id`, `digital_account_id`, `direction`, `amount_minor`, `signed_amount_minor`, `currency`, `line_memo` e `created_at`.
- `line.entry_id` deve ser igual ao `event.entry_id`.

## Fluxo de Hold de Saldo

```text
POST /holds
  -> valida payload
  -> verifica saldo disponivel
  -> cria hold HELD
  -> incrementa held_minor

POST /holds/{hold_id}/capture
  -> se HELD, muda para CAPTURED
  -> decrementa held_minor
  -> se ja CAPTURED, retorna sucesso idempotente

POST /holds/{hold_id}/release
  -> se HELD, muda para RELEASED
  -> decrementa held_minor
  -> se ja RELEASED, retorna sucesso idempotente
```

Regras de negocio:

- Hold reserva saldo disponivel, mas nao altera `posted_minor`.
- Criar hold exige saldo disponivel suficiente.
- Captura e liberacao fecham o hold e reduzem `held_minor`.
- Repetir a mesma operacao terminal e idempotente.
- Tentar capturar hold `RELEASED` ou liberar hold `CAPTURED` e transicao invalida.

Validacoes:

- `transfer_id`, `digital_account_id`, `amount_minor`, `currency`, `reason` e `expires_at` sao obrigatorios.
- `amount_minor` deve ser positivo.
- `currency` deve ter 3 letras.
- `expires_at` deve estar no futuro.
- `hold_id` e obrigatorio em capture/release.
- Consulta de extrato exige `digital_account_id`; `limit` deve ser positivo; `cursor` precisa ser valido quando informado.

## Outbox e Escala

`accounts` e `transfer` usam outbox para publicar eventos em Kafka sem depender de uma chamada direta no caminho transacional.

```text
PENDING
  -> claimPending com FOR UPDATE SKIP LOCKED
  -> PROCESSING com locked_by, locked_until e attempts incrementado
  -> publish Kafka com timeout
  -> PUBLISHED ou PENDING/FAILED
```

Regras de negocio e operacionais:

- Mais de uma replica pode rodar o publisher sem pegar o mesmo evento ao mesmo tempo.
- Eventos `PROCESSING` com `locked_until` expirado podem ser reclamados por outra replica.
- `markPublished` e `markFailed` so atualizam o evento se a replica dona do lock for a mesma.
- Apos `OUTBOX_PUBLISHER_MAX_ATTEMPTS`, o evento vai para `FAILED`.

Configuracoes relevantes:

```text
OUTBOX_PUBLISHER_FIXED_DELAY_MS
OUTBOX_PUBLISHER_BATCH_SIZE
OUTBOX_PUBLISHER_LOCK_LEASE_MS
OUTBOX_PUBLISHER_SEND_TIMEOUT_MS
OUTBOX_PUBLISHER_MAX_ATTEMPTS
KAFKA_PRODUCER_MAX_BLOCK_MS
```

## Tabela de Idempotencia

| Fluxo | Chave idempotente | Garantia |
| --- | --- | --- |
| `POST /accounts` | `Idempotency-Key` | Nao cria conta duplicada. |
| `account-created` no ledger | `account_code` / `digital_account_id` | Nao cria ledger account duplicada. |
| `POST /transfers` | `Idempotency-Key` | Nao cria transferencia interna duplicada. |
| Inbound externo | `source_institution_code` + `external_transfer_id` | Nao credita duas vezes a mesma transferencia externa. |
| Outbox accounts/transfer | `event_id` + lock | Nao publica o mesmo evento simultaneamente entre replicas. |
| `ledger-movements` | `external_id = transfer_id` | Nao cria posting duplicado; republica evento existente se necessario. |
| `ledger-reversals` | `reversal:<original_external_id>` | Nao cria duas reversoes para o mesmo posting original. |
| `ledger-posting-created` no balance | `entry_id` e `external_id` | Nao projeta duas vezes o mesmo posting. |
| Capture/release de hold | `hold_id` + status alvo | Repetir mesma operacao terminal retorna sucesso. |

## Estados Principais

Transferencia:

```text
RECEIVED
HOLD_CREATED
PSP_PENDING
PSP_CONFIRMED
POSTING_REQUESTED
COMPLETED
FAILED
EXPIRED
REVERSED
```

Estados terminais:

```text
COMPLETED
FAILED
EXPIRED
REVERSED
```

Hold:

```text
HELD
CAPTURED
RELEASED
EXPIRED
```

Conta:

```text
RECEIVED
BAAS_PENDING
ACTIVE
REJECTED
FAILED
```

## Contratos Kafka

| Topico | Produtor | Consumidor | Chave obrigatoria | Validacao de chave |
| --- | --- | --- | --- | --- |
| `account-created` | accounts | ledger | `digital_account_id` | chave igual ao payload. |
| `ledger-movements` | transfer | ledger | `source_digital_account_id` | chave igual ao payload. |
| `ledger-reversals` | externo/scripts | ledger | `original_external_id` | chave igual ao payload. |
| `yield-accruals` | yield | ledger | `digital_account_id` | rendimento diario D-1 com CDI usado persistido em `yield.daily_cdi_yield_rates`. |
| `ledger-posting-created` | ledger | balance, transfer | `external_id` | chave igual ao payload. |

Cada topico possui DLT com sufixo `.DLT`.

## Limites Conhecidos

- O sistema simula PSP e BaaS; nao implementa conciliacao real com instituicoes externas.
- Expiracao automatica de transferencia e de hold existe como estado/modelo, mas nao e um produto operacional completo de chargeback, disputa ou conciliacao.
- Tarifas, chargeback, contas de impostos e outras contas contabeis operacionais ainda nao formam um catalogo amplo.
- O `ledger` depende do seed das contas de liquidacao para inbound externo em ambientes novos.
- A stack Kubernetes atual e preparada para Minikube e simulacao. Autoscaling e PDBs estao documentados em `docs/kubernetes-autoscaling-disruption.md`; para producao ainda faltam registry, secrets gerenciados, TLS, politicas de rede, backups e retencao formal.

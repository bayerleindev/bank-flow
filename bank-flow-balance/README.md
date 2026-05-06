# bank-flow-balance

Servico responsavel por materializar saldos e extratos a partir dos postings contabeis emitidos pelo `bank-flow-ledger`.

O `bank-flow-balance` e um read model: ele nao decide se uma transferencia deve existir, nao executa lancamentos contabeis e nao calcula double-entry. Essas responsabilidades ficam no ledger. Este servico consome eventos ja postados, valida o contrato minimo, projeta os dados em PostgreSQL e expoe APIs de leitura otimizadas para saldo e extrato.

## Responsabilidades

- Consumir eventos `ledger-posting-created` publicados pelo ledger.
- Validar se o evento esta consistente para projecao de saldo.
- Garantir idempotencia na projecao por `entry_id` e `external_id`.
- Atualizar `account_balances` com saldo postado em minor units.
- Armazenar linhas historicas em `account_balance_entries`.
- Expor APIs HTTP para consulta de saldo e extrato.
- Expor health checks e metricas operacionais.

## Fora de escopo

- Criar contas contabeis.
- Decidir aprovacao de transferencias.
- Gerar postings double-entry.
- Decidir ou executar estornos.
- Ser fonte de verdade contabil.
- Substituir o ledger em auditoria financeira.

## Stack

- Java 21
- Spring Boot 4.0.6
- Spring Kafka
- Spring WebMVC
- Spring Actuator
- Flyway
- PostgreSQL
- Micrometer + Prometheus
- Testcontainers com PostgreSQL e Kafka

## Arquitetura

Pacotes principais:

```text
br.com.bankflow.balance
├── configs          # Jackson, Kafka, Clock
├── consumers        # consumidores Kafka
├── controllers      # API HTTP
├── domain           # modelos de evento e modelos de leitura
├── observability    # metricas e health checks
├── repositories     # persistencia e queries JDBC
└── services         # casos de uso de projecao e leitura
```

Fluxo principal:

```text
bank-flow-ledger
  └── publica ledger-posting-created
        └── bank-flow-balance
              ├── valida evento
              ├── registra idempotencia em processed_ledger_entries
              ├── grava linhas em account_balance_entries
              └── atualiza saldo em account_balances
```

## Topico consumido

Topico:

```text
ledger-posting-created
```

DLT:

```text
ledger-posting-created.DLT
```

Chave Kafka esperada:

```text
external_id
```

O consumer valida que a chave Kafka e igual ao campo `external_id` do payload. Isso preserva particionamento por posting e evita mensagens com chave incoerente.

## Contrato do evento

Exemplo de payload consumido:

```json
{
  "entry_id": 9001,
  "external_id": "50837338-642c-479d-8ab7-3feadfb59ee7",
  "entry_type": "TRANSFER",
  "status": "POSTED",
  "description": "Transferencia BRL de conta 123 para conta 456",
  "occurred_at": 1778000000000,
  "created_at": 1778000000100,
  "reversal_of_entry_id": 0,
  "metadata": "{}",
  "lines": [
    {
      "line_id": 9101,
      "entry_id": 9001,
      "account_id": 1001,
      "direction": "DEBIT",
      "amount_minor": 1500,
      "signed_amount_minor": -1500,
      "currency": "BRL",
      "line_memo": "source",
      "created_at": 1778000000100
    },
    {
      "line_id": 9102,
      "entry_id": 9001,
      "account_id": 2002,
      "direction": "CREDIT",
      "amount_minor": 1500,
      "signed_amount_minor": 1500,
      "currency": "BRL",
      "line_memo": "destination",
      "created_at": 1778000000100
    }
  ]
}
```

Validacoes aplicadas:

- `entry_id`, `occurred_at` e `created_at` devem ser positivos.
- `external_id`, `entry_type`, `status` e `description` sao obrigatorios.
- `status` deve ser `POSTED`.
- Deve existir pelo menos duas linhas.
- Cada linha deve ter `line_id`, `entry_id`, `account_id` e `created_at` positivos.
- `direction` deve ser `DEBIT` ou `CREDIT`.
- `amount_minor` deve ser positivo.
- `signed_amount_minor` deve ter sinal coerente:
  - `DEBIT`: negativo.
  - `CREDIT`: positivo.
- `abs(signed_amount_minor)` deve ser igual a `amount_minor`.
- `currency` deve ter 3 caracteres.
- A soma de `signed_amount_minor` deve ser zero por moeda.
- O `entry_id` de cada linha deve ser igual ao `entry_id` do evento.

## Modelo de dados

As tabelas sao criadas por Flyway em:

```text
src/main/resources/db/migration/V1__create_balance_projection_tables.sql
```

### account_balances

Tabela de saldo atual por conta.

```sql
CREATE TABLE account_balances (
    account_id BIGINT PRIMARY KEY,
    currency VARCHAR(3) NOT NULL,
    posted_minor BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL
);
```

Campos:

- `account_id`: identificador numerico da conta contabil gerado pelo ledger.
- `currency`: moeda ISO de 3 caracteres.
- `posted_minor`: saldo postado em minor units, por exemplo centavos para BRL.
- `updated_at`: timestamp numerico da ultima atualizacao.

### account_balance_entries

Tabela de linhas historicas usadas no extrato.

```sql
CREATE TABLE account_balance_entries (
    line_id BIGINT PRIMARY KEY,
    entry_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    entry_type VARCHAR(64) NOT NULL,
    direction VARCHAR(6) NOT NULL,
    amount_minor BIGINT NOT NULL,
    signed_amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(512) NOT NULL,
    occurred_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);
```

### processed_ledger_entries

Tabela de idempotencia.

```sql
CREATE TABLE processed_ledger_entries (
    entry_id BIGINT PRIMARY KEY,
    external_id VARCHAR(128) NOT NULL,
    processed_at BIGINT NOT NULL
);
```

Tambem existe indice unico em `external_id`.

## Idempotencia

Antes de projetar linhas e saldo, o servico tenta inserir o posting em `processed_ledger_entries`.

Se o `entry_id` ja existir, o evento e tratado como duplicado e nenhuma linha/saldo e alterado.

Esse comportamento protege contra:

- retry do consumer;
- replay de topico;
- republicacao acidental do mesmo posting;
- reprocessamento apos crash entre processamento e commit de offset.

## Transacao

A projecao roda em uma transacao Spring:

```text
processed_ledger_entries
account_balance_entries
account_balances
```

Se qualquer insert/update falhar, a transacao e revertida e a mensagem nao e confirmada. O error handler do Kafka aplica retry e, ao esgotar as tentativas, envia para a DLT.

## API HTTP

Porta padrao:

```text
8082
```

Pode ser alterada com:

```bash
SERVER_PORT=8083 ./gradlew bootRun
```

### GET /balances/{account_id}

Consulta o saldo atual projetado de uma conta.

Exemplo:

```bash
curl -s http://localhost:8082/balances/1778026147200000
```

Resposta:

```json
{
  "account_id": 1778026147200000,
  "currency": "BRL",
  "posted_minor": -269069,
  "updated_at": 1778098839069
}
```

Status:

- `200`: saldo encontrado.
- `400`: `account_id` invalido.
- `404`: ainda nao existe projecao de saldo para a conta.

### GET /balances/{account_id}/statement

Consulta o extrato paginado da conta.

Parametros:

- `limit`: tamanho da pagina. Padrao `50`, maximo `200`.
- `cursor`: cursor opaco retornado pela pagina anterior.

Primeira pagina:

```bash
curl -s "http://localhost:8082/balances/1778026147200000/statement?limit=2"
```

Resposta:

```json
{
  "balance": {
    "account_id": 1778026147200000,
    "currency": "BRL",
    "posted_minor": -269069,
    "updated_at": 1778098839069
  },
  "lines": [
    {
      "line_id": 1778098838981000001,
      "entry_id": 1778098838981000000,
      "account_id": 1778026147200000,
      "external_id": "50837338-642c-479d-8ab7-3feadfb59ee7",
      "entry_type": "TRANSFER",
      "direction": "DEBIT",
      "amount_minor": 4503,
      "signed_amount_minor": -4503,
      "currency": "BRL",
      "description": "Transferencia BRL de conta 33567-9 para conta 88373-8",
      "occurred_at": 1778098838836,
      "created_at": 1778098838836
    }
  ],
  "limit": 2,
  "next_cursor": "MTc3ODA5ODgwMjYzNzoxNzc4MDk4ODAyNzQ4MDAwMDAx"
}
```

Proxima pagina:

```bash
curl -s "http://localhost:8082/balances/1778026147200000/statement?limit=2&cursor=MTc3ODA5ODgwMjYzNzoxNzc4MDk4ODAyNzQ4MDAwMDAx"
```

Quando `next_cursor` vier `null`, nao ha proxima pagina.

### Cursor do extrato

O cursor e opaco para o cliente.

Internamente ele representa:

```text
occurred_at:line_id
```

Ele e codificado em Base64 URL-safe sem padding. Isso evita expor detalhes de ordenacao como contrato publico e permite mudar a implementacao no futuro mantendo a API como `cursor`.

A ordenacao do extrato e:

```sql
ORDER BY occurred_at DESC, line_id DESC
```

A pagina seguinte usa:

```sql
occurred_at < cursor.occurred_at
OR (occurred_at = cursor.occurred_at AND line_id < cursor.line_id)
```

Isso evita pular ou repetir itens quando varias linhas possuem o mesmo `occurred_at`.

## Erros HTTP

Erros seguem `application/problem+json`.

Exemplo de cursor invalido:

```json
{
  "detail": "cursor is invalid",
  "instance": "/balances/1778026147200000/statement",
  "status": 400,
  "title": "Invalid request"
}
```

Exemplo de saldo inexistente:

```json
{
  "detail": "No balance projection exists for account_id=123",
  "status": 404,
  "title": "Balance not found",
  "account_id": 123
}
```

## Configuracao

Variaveis suportadas:

| Variavel | Padrao | Descricao |
| --- | --- | --- |
| `SERVER_PORT` | `8082` | Porta HTTP da aplicacao. |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/bank_flow` | JDBC URL do Postgres. |
| `POSTGRES_USER` | `myuser` | Usuario do Postgres. |
| `POSTGRES_PASSWORD` | `mysecretpassword` | Senha do Postgres. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Bootstrap servers do Kafka. |
| `KAFKA_CONSUMER_GROUP_ID` | `bank-flow-balance` | Consumer group do servico. |
| `KAFKA_AUTO_OFFSET_RESET` | `earliest` | Offset reset policy. |
| `KAFKA_RETRY_INTERVAL_MS` | `1000` | Intervalo entre retries do consumer. |
| `KAFKA_RETRY_MAX_ATTEMPTS` | `3` | Tentativas totais antes de DLT. |
| `KAFKA_HEALTH_TIMEOUT_MS` | `2000` | Timeout do health check de Kafka. |
| `MANAGEMENT_HEALTH_SHOW_DETAILS` | `when_authorized` | Detalhamento do Actuator health. |

## Infra local

Subir infraestrutura:

```bash
docker compose up -d db kafka kafka-init kafka-ui
```

Kafka UI:

```text
http://localhost:8081
```

Postgres:

```text
localhost:5432
database: bank_flow
user: myuser
password: mysecretpassword
```

Topicos criados pelo `kafka-init`:

- `ledger-posting-created`
- `ledger-posting-created.DLT`

## Rodando a aplicacao

Dentro do diretorio do servico:

```bash
cd bank-flow-balance
./gradlew bootRun
```

Com porta alternativa:

```bash
SERVER_PORT=8083 ./gradlew bootRun
```

## Flyway

Flyway e executado pela aplicacao no startup.

Configuracao:

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

Validar migrations aplicadas no Postgres local:

```bash
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
```

## Observabilidade

Endpoints:

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
GET /actuator/metrics
GET /actuator/prometheus
```

### Liveness

Inclui:

- `livenessState`
- `ping`

Liveness nao depende de Postgres/Kafka.

### Readiness

Inclui:

- `readinessState`
- `db`
- `kafka`

Se Postgres ou Kafka ficarem indisponiveis, readiness deve cair e o servico pode sair de rotacao.

### Metricas customizadas

Metricas do fluxo de balance:

- `bank_flow_balance_kafka_messages_total`
- `bank_flow_balance_projection_total`
- `bank_flow_balance_projection_duration`
- `bank_flow_balance_projection_lines`

Metricas tambem disponiveis via Actuator/Micrometer:

- JVM
- HTTP server requests
- HikariCP
- JDBC
- Kafka consumer
- Spring Kafka listener
- Process/system

Exemplo:

```bash
curl -s http://localhost:8082/actuator/prometheus | rg "bank_flow_balance|kafka_consumer|http_server"
```

As metricas customizadas aparecem depois que eventos forem processados.

## Retry e DLT

O consumer usa ack manual e error handler com retry.

Configuracao padrao:

```properties
bank-flow.kafka.retry.interval-ms=1000
bank-flow.kafka.retry.max-attempts=3
```

Erros nao recuperaveis como payload invalido e chave Kafka invalida sao classificados como nao retryable e seguem para DLT conforme configuracao do Spring Kafka.

DLT:

```text
ledger-posting-created.DLT
```

## Testes

Rodar todos os testes:

```bash
./gradlew test
```

Cobertura atual:

- Testes de dominio/validacao de evento.
- Testes de service de projecao.
- Testes de service de query.
- Testes JDBC com H2/Flyway.
- Testcontainers com PostgreSQL real para repository e cursor.
- Testcontainers com Kafka + PostgreSQL para consumer end-to-end.

Testes de integracao relevantes:

```text
LedgerPostingCreatedConsumerIntegrationTests
JdbcBalanceQueryRepositoryPostgresIntegrationTests
```

Esses testes validam:

- Flyway em PostgreSQL real.
- Projecao do evento Kafka em saldo e extrato.
- Idempotencia ao republicar o mesmo evento.
- Paginacao robusta com cursor composto.

## Comandos uteis

Listar topicos:

```bash
docker compose exec -T kafka kafka-topics --bootstrap-server kafka:29092 --list
```

Consultar saldos no Postgres:

```bash
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM account_balances LIMIT 10;"
```

Consultar extrato projetado:

```bash
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM account_balance_entries ORDER BY occurred_at DESC LIMIT 10;"
```

Consultar eventos processados:

```bash
docker compose exec -T db psql -U myuser -d bank_flow -c "SELECT * FROM processed_ledger_entries ORDER BY processed_at DESC LIMIT 10;"
```

Consultar DLT no Kafka UI:

```text
http://localhost:8081/ui/clusters/bankflow/all-topics/ledger-posting-created.DLT/messages
```

## Semantica de saldo

Atualmente o servico projeta apenas:

```text
posted_minor
```

`posted_minor` representa o saldo contabil confirmado pelo ledger. Ele pode ser positivo ou negativo dependendo das linhas postadas para a conta.

O servico ainda nao modela:

- `pending_minor`
- `available_minor`
- `blocked_minor`

Esses campos exigem eventos especificos de autorizacao, reserva, captura, cancelamento ou liberacao. Enquanto o ledger publica apenas postings confirmados, o balance deve continuar tratando o saldo como saldo postado.

## Decisoes importantes

### Por que o balance consome evento em vez de acessar o banco do ledger?

Porque o balance e uma projecao. Ele deve ser desacoplado do storage interno do ledger e reagir a fatos de dominio publicados. Isso preserva autonomia dos servicos e permite rebuild/replay da projecao.

### Por que a idempotencia fica no balance?

Porque consumo Kafka e at-least-once. Mesmo que o ledger publique corretamente, o consumer pode receber a mesma mensagem mais de uma vez. O read model precisa ser seguro contra duplicidade.

### Por que cursor opaco no extrato?

Porque a ordenacao e detalhe interno. O cliente recebe `next_cursor` e devolve esse mesmo valor na proxima chamada. Isso evita acoplamento com `occurred_at`/`line_id` e permite evoluir a estrategia sem quebrar contrato.

## Pontos ainda pendentes para producao

- Autenticacao e autorizacao por conta/cliente.
- Rate limiting nos endpoints publicos.
- OpenAPI/Swagger.
- Reprocessamento operacional de DLT.
- Runbook de replay completo do read model.
- Alertas em cima de lag Kafka, DLT, falhas de projecao e readiness.
- Tracing distribuido.
- Separar porta de management, se necessario.
- Dashboard Prometheus/Grafana.
- Campos de saldo disponivel/bloqueado quando existirem eventos de reserva/autorizacao.

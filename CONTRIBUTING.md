# Contributing to Bank Flow Backend

Thanks for considering a contribution. Bank Flow is a learning and architecture project, so clarity matters as much as code.

## Before You Start

- Check the root [README.md](README.md) and the README for the service you plan to change.
- Keep changes small and focused. Avoid mixing refactors, formatting churn and behavior changes.
- For larger changes, open an issue first and describe the problem, proposed behavior and affected services.

## Local Setup

Start the shared infrastructure:

```bash
make compose-up-infra
```

Run only the services needed for your change. Each service README documents its local commands.

Useful test commands:

```bash
make test
cd bank-flow-accounts && ./gradlew test
cd bank-flow-outboxer && ./gradlew test
cd bank-flow-yield && ./gradlew test
cd bank-flow-ledger && ./gradlew test
cd bank-flow-balance && ./gradlew test
cd bank-flow-transfer && ./gradlew test
```

Some integration tests may need Docker because the project uses Postgres, Kafka, immudb and Testcontainers.

## Architecture Rules

- Public cross-service contracts use `digital_account_id`.
- Numeric accounting `account_id` values belong to `bank-flow-ledger`.
- Producer services write durable rows to `outboxer.outbox_events`; Kafka publishing stays centralized in `bank-flow-outboxer`.
- Kafka consumers should be idempotent and use deterministic keys.
- Preserve W3C trace propagation through HTTP, outbox rows and Kafka headers.
- Update dashboards and documentation when metrics, labels, spans or event contracts change.

## Pull Request Checklist

- The change has a clear reason and a focused scope.
- Tests were added or updated for non-trivial behavior.
- The relevant Gradle test task passed locally, or the PR explains why it was not run.
- API examples, event payloads, dashboards and README sections were updated when needed.
- New metrics and traces use stable names and labels.
- No generated logs, local duplicate files or editor artifacts are included.

## Commit And PR Style

Use concise commit messages that describe behavior, for example:

```text
Add trace context to ledger movement consumer
Fix Kafka lag query in Grafana dashboards
Document outbox event contract for yield accruals
```

In the PR description, include:

- What changed.
- Why it changed.
- How it was tested.
- Any migration or operational impact.

## Reporting Bugs

Please include:

- Service name and endpoint or Kafka topic.
- Steps to reproduce.
- Expected and actual behavior.
- Relevant logs, trace id, dashboard panel or metric query.
- Whether the issue happens locally, in Docker Compose or in Kubernetes.

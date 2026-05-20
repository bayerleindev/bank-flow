# Observability

Local observability stack with Grafana, Tempo, Loki, Alloy, and Prometheus.

## Run

```bash
make observability-deploy
```

## URLs

- Grafana: http://localhost:3000
- Alloy UI: http://localhost:12345
- Tempo: http://localhost:3200
- Loki: http://localhost:3100
- Prometheus: http://localhost:9090

Grafana credentials:

- user: `admin`
- password: `admin`

## OTLP Ingestion

Send application telemetry to Alloy:

- OTLP gRPC: `http://localhost:14317`
- OTLP HTTP: `http://localhost:14318`

Alloy forwards traces to Tempo and logs to Loki.

Tempo runs the metrics generator with:

- `service-graphs`, used by Grafana Explore > Tempo > Service Graph
- `span-metrics`, used by traces-to-metrics and latency/rate queries

The generated trace metrics are written to Prometheus through remote write.

## Dashboards

Grafana provisions dashboards copied by the root `Makefile` from each service's `dashboards` directory into `observability/grafana/dashboards`.

For Accounts, the source dashboards live in:

```text
bank-flow-accounts/dashboards
```

Deploy dashboards for the default service:

```bash
make dashboards-deploy
```

Deploy dashboards for another service:

```bash
make dashboards-deploy SERVICE=bank-flow-other-service
```

Deploy dashboards and start/recreate the observability stack:

```bash
make observability-deploy SERVICE=bank-flow-accounts
```

Accounts currently provides:

- `Accounts - Golden Signals`
- `Accounts - Business`

Prometheus scrapes:

- Accounts API: `host.docker.internal:8080/actuator/prometheus`
- Accounts Worker: `host.docker.internal:8081/actuator/prometheus`
- Tempo: `tempo:3200/metrics`
- Alloy: `alloy:12345/metrics`

## Validate Service Graph

Restart the API and worker after changing tracing dependencies, create a new account, then check:

```bash
curl -s "http://localhost:9090/api/v1/query?query=traces_service_graph_request_total" | jq .
curl -s "http://localhost:9090/api/v1/query?query=traces_spanmetrics_calls_total" | jq .
```

In Grafana, open `Explore`, select `Tempo`, and use the `Service Graph` view. Kafka topics are exposed through messaging labels such as `messaging.destination.name`, `messaging.destination`, `messaging.source.name`, and `messaging.kafka.topic` when they are present in the spans.

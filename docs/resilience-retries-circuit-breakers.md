# Resilience, retries and circuit breakers

Bank Flow uses explicit resilience policies around synchronous HTTP dependencies. The goal is to fail fast, retry only transient failures and avoid amplifying outages under high throughput.

## HTTP clients

The following clients are protected with Resilience4j retry and circuit breaker:

- `bank-flow-accounts`: BaaS HTTP client.
- `bank-flow-transfer`: accounts and balance HTTP clients.
- `bank-flow-yield`: BCB CDI HTTP client.

Retries are intentionally small by default:

- API-to-API calls: 2 attempts, 100 ms exponential backoff.
- BCB CDI call: 2 attempts, 500 ms exponential backoff.

Only transient failures are retried and recorded by the circuit breaker:

- connection/read failures reported as `ResourceAccessException`;
- HTTP `429`;
- HTTP `5xx`.

HTTP `4xx` errors, except `429`, are treated as business/client responses and are not retried.

When the circuit breaker is open, APIs return `503 Service Unavailable`. Downstream HTTP responses that still reach the service continue to be mapped as gateway/downstream failures.

## Kafka consumers

Kafka consumers still use Spring Kafka's `DefaultErrorHandler` with DLT publishing. This is intentional: Kafka retry is tied to offset management, manual ack and dead-letter routing, so it belongs in the Kafka listener layer instead of Resilience4j.

The defaults are conservative for high volume:

- retry attempts are limited;
- validation/deserialization failures are not retried;
- failed records are published to `<topic>.DLT`;
- consumer lag and DLQ volume are covered by dashboards and alerts.

## Tuning

For Kubernetes deployments, tune the values through chart environment variables:

- `HTTP_CLIENT_CONNECT_TIMEOUT_MS`
- `HTTP_CLIENT_READ_TIMEOUT_MS`
- `HTTP_RETRY_MAX_ATTEMPTS`
- `HTTP_RETRY_BACKOFF_MS`
- `HTTP_RETRY_MULTIPLIER`
- `HTTP_CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD`
- `HTTP_CIRCUIT_BREAKER_SLIDING_WINDOW_SIZE`
- `HTTP_CIRCUIT_BREAKER_MINIMUM_CALLS`
- `HTTP_CIRCUIT_BREAKER_OPEN_DURATION_MS`

For a target of roughly 200k operations/day, prefer adding replicas/partitions and keeping retries low. Increasing retries globally can multiply load during incidents and make recovery slower.

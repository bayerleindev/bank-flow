package br.com.bankflow.transfer.repositories;

import br.com.bankflow.transfer.domain.OutboxEvent;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class JdbcOutboxEventRepository implements OutboxEventRepository {
	private final JdbcTemplate jdbcTemplate;
	private final String producerService;
	private final Tracer tracer;

	public JdbcOutboxEventRepository(
			JdbcTemplate jdbcTemplate,
			ObjectProvider<Tracer> tracerProvider,
			@Value("${bank-flow.outbox.producer-service:bank-flow-transfer}") String producerService
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.producerService = producerService;
		this.tracer = tracerProvider.getIfAvailable();
	}

	@Override
	public void createIfAbsent(OutboxEvent event) {
		Map<String, String> traceHeaders = currentTraceHeaders();
		try {
			jdbcTemplate.update("""
					INSERT INTO outboxer.outbox_events (
						event_id, producer_service, aggregate_type, aggregate_id, event_type, topic, event_key,
						payload, status, attempts, last_error, created_at, published_at, traceparent, tracestate
					) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					event.eventId(),
					producerService,
					event.aggregateType(),
					event.aggregateId(),
					event.eventType(),
					event.topic(),
					event.eventKey(),
					event.payload(),
					event.status(),
					event.attempts(),
					event.lastError(),
					event.createdAt(),
					event.publishedAt(),
					firstNonBlank(event.traceparent(), traceHeaders.get("traceparent")),
					firstNonBlank(event.tracestate(), traceHeaders.get("tracestate"))
			);
		} catch (DuplicateKeyException ignored) {
			// Duplicate PSP confirmations must not enqueue duplicate ledger commands.
		}
	}

	@Override
	public long countPending() {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM outboxer.outbox_events
				WHERE producer_service = ?
				  AND (status = 'PENDING'
				       OR (status = 'PROCESSING' AND locked_until < ?))
				""", Long.class, producerService, nowMillis());
		return count == null ? 0 : count;
	}

	@Override
	public double oldestPendingEventAgeSeconds(long nowMillis) {
		Long oldestCreatedAt = jdbcTemplate.queryForObject("""
				SELECT MIN(created_at)
				FROM outboxer.outbox_events
				WHERE producer_service = ?
				  AND (status = 'PENDING'
				       OR (status = 'PROCESSING' AND locked_until < ?))
				""", Long.class, producerService, nowMillis);
		if (oldestCreatedAt == null) {
			return 0;
		}
		return Math.max(0, nowMillis - oldestCreatedAt) / 1000.0;
	}

	private long nowMillis() {
		return System.currentTimeMillis();
	}

	private Map<String, String> currentTraceHeaders() {
		Span currentSpan = tracer == null ? null : tracer.currentSpan();
		if (currentSpan == null) {
			return Map.of();
		}
		TraceContext context = currentSpan.context();
		Map<String, String> headers = new HashMap<>();
		headers.put("traceparent", traceparent(context));
		return headers;
	}

	private String traceparent(TraceContext context) {
		String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
		return "00-%s-%s-%s".formatted(context.traceId(), context.spanId(), flags);
	}

	private String firstNonBlank(String preferred, String fallback) {
		if (preferred != null && !preferred.isBlank()) {
			return preferred;
		}
		return fallback == null || fallback.isBlank() ? null : fallback;
	}
}

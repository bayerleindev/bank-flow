package br.com.bankflow.accounts.repositories;

import br.com.bankflow.accounts.domain.OutboxEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboxEventRepository implements OutboxEventRepository {
	private final JdbcTemplate jdbcTemplate;
	private final String producerService;

	public JdbcOutboxEventRepository(
			JdbcTemplate jdbcTemplate,
			@Value("${bank-flow.outbox.producer-service:${spring.application.name}}") String producerService
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.producerService = producerService;
	}

	@Override
	public void createIfAbsent(OutboxEvent event) {
		try {
			jdbcTemplate.update("""
					INSERT INTO outboxer.outbox_events (
						event_id, producer_service, aggregate_type, aggregate_id, event_type, topic, event_key,
						payload, status, attempts, last_error, created_at, published_at
					) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					event.eventId(), producerService, event.aggregateType(), event.aggregateId(), event.eventType(),
					event.topic(), event.eventKey(), event.payload(), event.status(), event.attempts(),
					event.lastError(), event.createdAt(), event.publishedAt()
			);
		} catch (DuplicateKeyException ignored) {
			// Duplicate account activation must not publish duplicate account-created events.
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
}

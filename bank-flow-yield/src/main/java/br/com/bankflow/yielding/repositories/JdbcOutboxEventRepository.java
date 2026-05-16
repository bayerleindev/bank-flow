package br.com.bankflow.yielding.repositories;

import br.com.bankflow.yielding.domain.OutboxEvent;
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
			// Duplicate D-1 processing must not publish duplicate yield accruals.
		}
	}
}

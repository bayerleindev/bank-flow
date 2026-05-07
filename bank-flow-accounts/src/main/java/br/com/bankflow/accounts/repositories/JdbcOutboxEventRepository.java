package br.com.bankflow.accounts.repositories;

import br.com.bankflow.accounts.domain.OutboxEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcOutboxEventRepository implements OutboxEventRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcOutboxEventRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void createIfAbsent(OutboxEvent event) {
		try {
			jdbcTemplate.update("""
					INSERT INTO outbox_events (
						event_id, aggregate_type, aggregate_id, event_type, topic, event_key,
						payload, status, attempts, last_error, created_at, published_at
					) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					event.eventId(), event.aggregateType(), event.aggregateId(), event.eventType(),
					event.topic(), event.eventKey(), event.payload(), event.status(), event.attempts(),
					event.lastError(), event.createdAt(), event.publishedAt()
			);
		} catch (DuplicateKeyException ignored) {
			// Duplicate account activation must not publish duplicate account-created events.
		}
	}

	@Override
	public List<OutboxEvent> findPending(int limit) {
		return jdbcTemplate.query("""
				SELECT event_id, aggregate_type, aggregate_id, event_type, topic, event_key, payload,
				       status, attempts, last_error, created_at, published_at
				FROM outbox_events
				WHERE status = 'PENDING'
				ORDER BY created_at
				LIMIT ?
				""", this::mapEvent, limit);
	}

	@Override
	public long countPending() {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM outbox_events
				WHERE status = 'PENDING'
				""", Long.class);
		return count == null ? 0 : count;
	}

	@Override
	public double oldestPendingEventAgeSeconds(long nowMillis) {
		Long oldestCreatedAt = jdbcTemplate.queryForObject("""
				SELECT MIN(created_at)
				FROM outbox_events
				WHERE status = 'PENDING'
				""", Long.class);
		if (oldestCreatedAt == null) {
			return 0;
		}
		return Math.max(0, nowMillis - oldestCreatedAt) / 1000.0;
	}

	@Override
	public void markPublished(UUID eventId, long publishedAt) {
		jdbcTemplate.update("""
				UPDATE outbox_events
				SET status = 'PUBLISHED',
				    published_at = ?,
				    last_error = NULL
				WHERE event_id = ?
				""", publishedAt, eventId);
	}

	@Override
	public void markFailed(UUID eventId, String errorMessage) {
		jdbcTemplate.update("""
				UPDATE outbox_events
				SET attempts = attempts + 1,
				    last_error = ?
				WHERE event_id = ?
				""", truncate(errorMessage), eventId);
	}

	private String truncate(String errorMessage) {
		if (errorMessage == null) {
			return null;
		}
		return errorMessage.length() <= 512 ? errorMessage : errorMessage.substring(0, 512);
	}

	private OutboxEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
		return new OutboxEvent(
				(UUID) rs.getObject("event_id"),
				rs.getString("aggregate_type"),
				rs.getString("aggregate_id"),
				rs.getString("event_type"),
				rs.getString("topic"),
				rs.getString("event_key"),
				rs.getString("payload"),
				rs.getString("status"),
				rs.getInt("attempts"),
				rs.getString("last_error"),
				rs.getLong("created_at"),
				rs.getObject("published_at", Long.class)
		);
	}
}

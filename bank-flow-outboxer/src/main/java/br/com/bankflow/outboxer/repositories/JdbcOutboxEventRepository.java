package br.com.bankflow.outboxer.repositories;

import br.com.bankflow.outboxer.domain.OutboxEvent;
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
	public List<OutboxEvent> claimPending(
			int limit,
			String lockedBy,
			long nowMillis,
			long lockedUntilMillis,
			int maxAttempts
	) {
		return jdbcTemplate.query("""
				WITH candidates AS (
				    SELECT event_id
				    FROM outboxer.outbox_events
				    WHERE attempts < ?
				      AND (status = 'PENDING'
				           OR (status = 'PROCESSING' AND locked_until < ?))
				    ORDER BY created_at
				    FOR UPDATE SKIP LOCKED
				    LIMIT ?
				)
				UPDATE outboxer.outbox_events event
				SET status = 'PROCESSING',
				    attempts = attempts + 1,
				    locked_by = ?,
				    locked_until = ?,
				    last_error = NULL
				FROM candidates
				WHERE event.event_id = candidates.event_id
				RETURNING event.event_id, event.producer_service, event.aggregate_type, event.aggregate_id,
				          event.event_type, event.topic, event.event_key, event.payload, event.status,
				          event.attempts, event.last_error, event.created_at, event.published_at
				""", this::mapEvent, maxAttempts, nowMillis, limit, lockedBy, lockedUntilMillis);
	}

	@Override
	public long countPending() {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM outboxer.outbox_events
				WHERE status = 'PENDING'
				   OR (status = 'PROCESSING' AND locked_until < ?)
				""", Long.class, nowMillis());
		return count == null ? 0 : count;
	}

	@Override
	public double oldestPendingEventAgeSeconds(long nowMillis) {
		Long oldestCreatedAt = jdbcTemplate.queryForObject("""
				SELECT MIN(created_at)
				FROM outboxer.outbox_events
				WHERE status = 'PENDING'
				   OR (status = 'PROCESSING' AND locked_until < ?)
				""", Long.class, nowMillis);
		if (oldestCreatedAt == null) {
			return 0;
		}
		return Math.max(0, nowMillis - oldestCreatedAt) / 1000.0;
	}

	@Override
	public void markPublished(UUID eventId, long publishedAt, String lockedBy) {
		jdbcTemplate.update("""
				UPDATE outboxer.outbox_events
				SET status = 'PUBLISHED',
				    published_at = ?,
				    last_error = NULL,
				    locked_by = NULL,
				    locked_until = NULL
				WHERE event_id = ?
				  AND locked_by = ?
				""", publishedAt, eventId, lockedBy);
	}

	@Override
	public void markFailed(UUID eventId, String errorMessage, String lockedBy, int maxAttempts) {
		jdbcTemplate.update("""
				UPDATE outboxer.outbox_events
				SET status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
				    last_error = ?,
				    locked_by = NULL,
				    locked_until = NULL
				WHERE event_id = ?
				  AND locked_by = ?
				""", maxAttempts, truncate(errorMessage), eventId, lockedBy);
	}

	private long nowMillis() {
		return System.currentTimeMillis();
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
				rs.getString("producer_service"),
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

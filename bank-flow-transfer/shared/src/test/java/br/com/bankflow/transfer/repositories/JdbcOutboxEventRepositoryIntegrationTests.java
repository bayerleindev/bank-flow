package br.com.bankflow.transfer.repositories;

import br.com.bankflow.transfer.domain.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class JdbcOutboxEventRepositoryIntegrationTests {

    @Autowired
    private JdbcOutboxEventRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS outboxer");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS outboxer.outbox_events (
                    event_id UUID PRIMARY KEY,
                    producer_service VARCHAR(128),
                    aggregate_type VARCHAR(64) NOT NULL,
                    aggregate_id VARCHAR(128) NOT NULL,
                    event_type VARCHAR(128) NOT NULL,
                    topic VARCHAR(128) NOT NULL,
                    event_key VARCHAR(128) NOT NULL,
                    payload TEXT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    attempts INT NOT NULL DEFAULT 0,
                    last_error VARCHAR(512),
                    created_at BIGINT NOT NULL,
                    published_at BIGINT,
                    locked_by VARCHAR(128),
                    locked_until BIGINT
                )
                """);
        jdbcTemplate.execute("DELETE FROM outboxer.outbox_events");
    }

    @Test
    void createsEventIfAbsent() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                eventId, "Transfer", "agg-1", "type-1", "topic-1", "key-1", "{}", "PENDING", 0, null, System.currentTimeMillis(), null
        );

        repository.createIfAbsent(event);

        long count = repository.countPending();
        assertEquals(1, count);

        repository.createIfAbsent(event);
        assertEquals(1, repository.countPending());
    }

    @Test
    void countsPendingEvents() {
        jdbcTemplate.update("INSERT INTO outboxer.outbox_events (event_id, producer_service, aggregate_type, aggregate_id, event_type, topic, event_key, payload, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "bank-flow-transfer", "T", "1", "E", "topic", "k", "{}", "PENDING", System.currentTimeMillis());
        jdbcTemplate.update("INSERT INTO outboxer.outbox_events (event_id, producer_service, aggregate_type, aggregate_id, event_type, topic, event_key, payload, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "bank-flow-transfer", "T", "2", "E", "topic", "k", "{}", "PUBLISHED", System.currentTimeMillis());

        assertEquals(1, repository.countPending());
    }

    @Test
    void calculatesOldestPendingEventAge() {
        long now = System.currentTimeMillis();
        long tenSecondsAgo = now - 10_000;

        jdbcTemplate.update("INSERT INTO outboxer.outbox_events (event_id, producer_service, aggregate_type, aggregate_id, event_type, topic, event_key, payload, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "bank-flow-transfer", "T", "1", "E", "topic", "k", "{}", "PENDING", tenSecondsAgo);

        double age = repository.oldestPendingEventAgeSeconds(now);
        assertTrue(age >= 10.0);
    }
}

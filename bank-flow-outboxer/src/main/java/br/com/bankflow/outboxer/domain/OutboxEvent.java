package br.com.bankflow.outboxer.domain;

import java.util.UUID;

public record OutboxEvent(
		UUID eventId,
		String producerService,
		String aggregateType,
		String aggregateId,
		String eventType,
		String topic,
		String eventKey,
		String payload,
		String status,
		int attempts,
		String lastError,
		long createdAt,
		Long publishedAt,
		String traceparent,
		String tracestate
) {
	public OutboxEvent(
			UUID eventId,
			String producerService,
			String aggregateType,
			String aggregateId,
			String eventType,
			String topic,
			String eventKey,
			String payload,
			String status,
			int attempts,
			String lastError,
			long createdAt,
			Long publishedAt
	) {
		this(eventId, producerService, aggregateType, aggregateId, eventType, topic, eventKey, payload, status,
				attempts, lastError, createdAt, publishedAt, null, null);
	}
}

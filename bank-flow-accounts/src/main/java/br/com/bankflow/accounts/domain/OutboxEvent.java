package br.com.bankflow.accounts.domain;

import java.util.UUID;

public record OutboxEvent(
		UUID eventId,
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
}

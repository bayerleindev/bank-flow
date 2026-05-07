package br.com.bankflow.transfer.repositories;

import br.com.bankflow.transfer.domain.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {
	void createIfAbsent(OutboxEvent event);

	List<OutboxEvent> findPending(int limit);

	default long countPending() {
		return 0;
	}

	default double oldestPendingEventAgeSeconds(long nowMillis) {
		return 0;
	}

	void markPublished(UUID eventId, long publishedAt);

	void markFailed(UUID eventId, String errorMessage);
}

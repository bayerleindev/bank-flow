package br.com.bankflow.transfer.repositories;

import br.com.bankflow.transfer.domain.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {
	void createIfAbsent(OutboxEvent event);

	List<OutboxEvent> claimPending(int limit, String lockedBy, long nowMillis, long lockedUntilMillis, int maxAttempts);

	default long countPending() {
		return 0;
	}

	default double oldestPendingEventAgeSeconds(long nowMillis) {
		return 0;
	}

	void markPublished(UUID eventId, long publishedAt, String lockedBy);

	void markFailed(UUID eventId, String errorMessage, String lockedBy, int maxAttempts);
}

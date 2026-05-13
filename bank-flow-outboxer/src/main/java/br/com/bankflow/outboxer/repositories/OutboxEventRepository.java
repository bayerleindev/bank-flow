package br.com.bankflow.outboxer.repositories;

import br.com.bankflow.outboxer.domain.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {
	List<OutboxEvent> claimPending(int limit, String lockedBy, long nowMillis, long lockedUntilMillis, int maxAttempts);

	long countPending();

	double oldestPendingEventAgeSeconds(long nowMillis);

	void markPublished(UUID eventId, long publishedAt, String lockedBy);

	void markFailed(UUID eventId, String errorMessage, String lockedBy, int maxAttempts);
}

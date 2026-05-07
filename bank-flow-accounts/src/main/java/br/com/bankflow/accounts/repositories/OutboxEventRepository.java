package br.com.bankflow.accounts.repositories;

import br.com.bankflow.accounts.domain.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {
	void createIfAbsent(OutboxEvent event);

	List<OutboxEvent> findPending(int limit);

	void markPublished(UUID eventId, long publishedAt);

	void markFailed(UUID eventId, String errorMessage);
}

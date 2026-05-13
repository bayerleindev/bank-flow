package br.com.bankflow.transfer.repositories;

import br.com.bankflow.transfer.domain.OutboxEvent;

public interface OutboxEventRepository {
	void createIfAbsent(OutboxEvent event);

	default long countPending() {
		return 0;
	}

	default double oldestPendingEventAgeSeconds(long nowMillis) {
		return 0;
	}
}

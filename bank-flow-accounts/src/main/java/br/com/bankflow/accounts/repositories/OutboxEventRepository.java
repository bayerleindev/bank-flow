package br.com.bankflow.accounts.repositories;

import br.com.bankflow.accounts.domain.OutboxEvent;

public interface OutboxEventRepository {
	void createIfAbsent(OutboxEvent event);

	default long countPending() {
		return 0;
	}

	default double oldestPendingEventAgeSeconds(long nowMillis) {
		return 0;
	}
}

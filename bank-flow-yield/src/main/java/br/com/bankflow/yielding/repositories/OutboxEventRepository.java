package br.com.bankflow.yielding.repositories;

import br.com.bankflow.yielding.domain.OutboxEvent;

public interface OutboxEventRepository {
	void createIfAbsent(OutboxEvent event);
}

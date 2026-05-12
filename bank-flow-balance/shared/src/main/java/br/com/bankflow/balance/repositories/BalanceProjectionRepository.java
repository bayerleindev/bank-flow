package br.com.bankflow.balance.repositories;

import br.com.bankflow.balance.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.balance.domain.LedgerPostingCreatedLine;

public interface BalanceProjectionRepository {
	boolean markProcessedIfAbsent(LedgerPostingCreatedEvent event, long processedAt);

	void saveEntryLine(LedgerPostingCreatedEvent event, LedgerPostingCreatedLine line);

	void applyPostedBalance(LedgerPostingCreatedLine line, long updatedAt);
}

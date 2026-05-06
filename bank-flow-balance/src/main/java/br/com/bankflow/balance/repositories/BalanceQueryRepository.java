package br.com.bankflow.balance.repositories;

import br.com.bankflow.balance.domain.AccountBalance;
import br.com.bankflow.balance.domain.AccountStatementLine;

import java.util.List;
import java.util.Optional;

public interface BalanceQueryRepository {
	Optional<AccountBalance> findBalance(long accountId);

	List<AccountStatementLine> findStatementLines(long accountId, int limit, StatementCursor cursor);

	record StatementCursor(long occurredAt, long lineId) {
	}
}

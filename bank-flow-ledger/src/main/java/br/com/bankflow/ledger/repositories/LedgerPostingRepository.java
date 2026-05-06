package br.com.bankflow.ledger.repositories;

import br.com.bankflow.ledger.domain.LedgerPosting;

import java.util.Optional;

public interface LedgerPostingRepository {
	boolean saveIfNotExists(LedgerPosting posting);

	Optional<LedgerPosting> findByExternalId(String externalId);

	boolean reversalExistsFor(long entryId);
}

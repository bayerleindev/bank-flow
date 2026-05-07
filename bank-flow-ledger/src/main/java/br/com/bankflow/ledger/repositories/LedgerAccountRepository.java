package br.com.bankflow.ledger.repositories;

import br.com.bankflow.ledger.domain.LedgerAccount;

import java.util.OptionalLong;
import java.util.UUID;

public interface LedgerAccountRepository {
	boolean saveIfNotExists(LedgerAccount account);

	OptionalLong findAccountIdByDigitalAccountId(UUID digitalAccountId);
}

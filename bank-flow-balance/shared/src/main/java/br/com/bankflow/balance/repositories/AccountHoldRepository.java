package br.com.bankflow.balance.repositories;

import br.com.bankflow.balance.domain.AccountHold;
import br.com.bankflow.balance.domain.CreateAccountHoldCommand;

import java.util.Optional;

public interface AccountHoldRepository {
	Optional<AccountHold> findByTransferId(String transferId);

	Optional<AccountHold> findByHoldId(String holdId);

	AccountHold createHeld(String holdId, CreateAccountHoldCommand command, long now);

	boolean reserveBalance(java.util.UUID digitalAccountId, String currency, long amountMinor, long updatedAt);

	boolean captureHeld(String holdId, long updatedAt);

	boolean releaseHeld(String holdId, long updatedAt);

	default int expireHeld(long now) {
		return 0;
	}
}

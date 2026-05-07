package br.com.bankflow.accounts.repositories;

import br.com.bankflow.accounts.clients.baas.BaasAccountResponse;
import br.com.bankflow.accounts.domain.Account;
import br.com.bankflow.accounts.domain.AccountStatus;
import br.com.bankflow.accounts.domain.CreateAccountCommand;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
	Optional<Account> findByIdempotencyKey(String idempotencyKey);

	Optional<Account> findByDocumentNumber(String documentNumber);

	Optional<Account> findByAccountId(UUID accountId);

	Account create(UUID accountId, UUID ownerId, CreateAccountCommand command, long now);

	Account updateFromBaas(UUID accountId, BaasAccountResponse response, AccountStatus status, String failureReason, long now);
}

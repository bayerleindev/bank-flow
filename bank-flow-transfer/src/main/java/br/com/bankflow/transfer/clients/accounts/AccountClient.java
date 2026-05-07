package br.com.bankflow.transfer.clients.accounts;

import java.util.UUID;

public interface AccountClient {
	AccountResponse getAccount(UUID digitalAccountId);
}

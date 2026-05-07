package br.com.bankflow.accounts.clients.baas;

import br.com.bankflow.accounts.domain.CreateAccountCommand;

public interface BaasClient {
	BaasAccountResponse createAccount(CreateAccountCommand command);
}

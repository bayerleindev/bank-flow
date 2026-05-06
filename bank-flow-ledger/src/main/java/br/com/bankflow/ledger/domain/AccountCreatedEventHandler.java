package br.com.bankflow.ledger.domain;

import br.com.bankflow.ledger.services.AccountCreatedService;

import org.springframework.stereotype.Service;

@Service
public class AccountCreatedEventHandler {
	private final AccountCreatedService accountCreatedService;

	public AccountCreatedEventHandler(AccountCreatedService accountCreatedService) {
		this.accountCreatedService = accountCreatedService;
	}

	public void handle(AccountCreatedEvent event) {
		accountCreatedService.createLedgerAccount(event);
	}
}

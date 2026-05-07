package br.com.bankflow.accounts.services;

public class AccountNotFoundException extends RuntimeException {
	private final String identifier;

	public AccountNotFoundException(String identifier) {
		super("account not found: " + identifier);
		this.identifier = identifier;
	}

	public String identifier() {
		return identifier;
	}
}

package br.com.bankflow.balance.services;

public class AccountHoldNotFoundException extends RuntimeException {
	private final String holdId;

	public AccountHoldNotFoundException(String holdId) {
		super("hold not found hold_id=%s".formatted(holdId));
		this.holdId = holdId;
	}

	public String holdId() {
		return holdId;
	}
}

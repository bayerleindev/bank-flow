package br.com.bankflow.balance.services;

public class BalanceNotFoundException extends RuntimeException {
	private final java.util.UUID digitalAccountId;

	public BalanceNotFoundException(java.util.UUID digitalAccountId) {
		super("balance not found digital_account_id=%s".formatted(digitalAccountId));
		this.digitalAccountId = digitalAccountId;
	}

	public java.util.UUID digitalAccountId() {
		return digitalAccountId;
	}
}

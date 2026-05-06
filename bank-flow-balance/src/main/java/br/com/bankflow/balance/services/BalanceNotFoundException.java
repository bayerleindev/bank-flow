package br.com.bankflow.balance.services;

public class BalanceNotFoundException extends RuntimeException {
	private final long accountId;

	public BalanceNotFoundException(long accountId) {
		super("balance not found account_id=%d".formatted(accountId));
		this.accountId = accountId;
	}

	public long accountId() {
		return accountId;
	}
}

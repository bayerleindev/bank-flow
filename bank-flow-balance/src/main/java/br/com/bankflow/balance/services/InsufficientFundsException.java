package br.com.bankflow.balance.services;

public class InsufficientFundsException extends RuntimeException {
	private final long accountId;
	private final long amountMinor;
	private final String currency;

	public InsufficientFundsException(long accountId, long amountMinor, String currency) {
		super("insufficient available balance account_id=%d amount_minor=%d currency=%s"
				.formatted(accountId, amountMinor, currency));
		this.accountId = accountId;
		this.amountMinor = amountMinor;
		this.currency = currency;
	}

	public long accountId() {
		return accountId;
	}

	public long amountMinor() {
		return amountMinor;
	}

	public String currency() {
		return currency;
	}
}

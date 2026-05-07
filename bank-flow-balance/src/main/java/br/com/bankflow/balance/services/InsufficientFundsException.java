package br.com.bankflow.balance.services;

public class InsufficientFundsException extends RuntimeException {
	private final java.util.UUID digitalAccountId;
	private final long amountMinor;
	private final String currency;

	public InsufficientFundsException(java.util.UUID digitalAccountId, long amountMinor, String currency) {
		super("insufficient available balance digital_account_id=%s amount_minor=%d currency=%s"
				.formatted(digitalAccountId, amountMinor, currency));
		this.digitalAccountId = digitalAccountId;
		this.amountMinor = amountMinor;
		this.currency = currency;
	}

	public java.util.UUID digitalAccountId() {
		return digitalAccountId;
	}

	public long amountMinor() {
		return amountMinor;
	}

	public String currency() {
		return currency;
	}
}

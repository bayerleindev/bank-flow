package br.com.bankflow.balance.services;

import br.com.bankflow.balance.domain.AccountHoldStatus;

public class AccountHoldStateException extends RuntimeException {
	private final String holdId;
	private final AccountHoldStatus status;

	public AccountHoldStateException(String holdId, AccountHoldStatus status) {
		super("hold cannot be changed hold_id=%s status=%s".formatted(holdId, status));
		this.holdId = holdId;
		this.status = status;
	}

	public String holdId() {
		return holdId;
	}

	public AccountHoldStatus status() {
		return status;
	}
}

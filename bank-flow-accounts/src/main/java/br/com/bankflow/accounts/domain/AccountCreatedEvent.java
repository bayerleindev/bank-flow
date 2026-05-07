package br.com.bankflow.accounts.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AccountCreatedEvent(
		@JsonProperty("digital_account_id") UUID digitalAccountId,
		String branch,
		String account,
		String currency
) {
	public static AccountCreatedEvent from(Account account) {
		return new AccountCreatedEvent(
				account.accountId(),
				account.branch(),
				account.account(),
				account.currency()
		);
	}
}

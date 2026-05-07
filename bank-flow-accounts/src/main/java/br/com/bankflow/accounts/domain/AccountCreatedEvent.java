package br.com.bankflow.accounts.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AccountCreatedEvent(
		@JsonProperty("owner_id") UUID ownerId,
		String branch,
		String account,
		String currency
) {
	public static AccountCreatedEvent from(Account account) {
		return new AccountCreatedEvent(
				account.ownerId(),
				account.branch(),
				account.account(),
				account.currency()
		);
	}
}

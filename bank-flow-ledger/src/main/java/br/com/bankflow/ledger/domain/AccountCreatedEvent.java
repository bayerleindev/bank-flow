package br.com.bankflow.ledger.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AccountCreatedEvent(
		@JsonProperty("owner_id") UUID ownerId,
		String branch,
		String account,
		String currency
) {
	public void validate() {
		if (ownerId == null) {
			throw new IllegalArgumentException("owner_id is required");
		}
		if (isBlank(branch)) {
			throw new IllegalArgumentException("branch is required");
		}
		if (isBlank(account)) {
			throw new IllegalArgumentException("account is required");
		}
		if (isBlank(currency)) {
			throw new IllegalArgumentException("currency is required");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}

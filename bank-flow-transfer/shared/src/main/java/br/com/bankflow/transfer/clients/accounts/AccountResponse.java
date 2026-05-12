package br.com.bankflow.transfer.clients.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AccountResponse(
		@JsonProperty("digital_account_id") UUID digitalAccountId,
		String branch,
		String account,
		String currency,
		String status
) {
	public void validateActive(String side) {
		if (digitalAccountId == null) {
			throw new IllegalArgumentException(side + "_digital_account_id not found");
		}
		if (!"ACTIVE".equals(status)) {
			throw new IllegalArgumentException(side + " account must be ACTIVE");
		}
		if (account == null || account.isBlank()) {
			throw new IllegalArgumentException(side + " account number is required");
		}
	}
}

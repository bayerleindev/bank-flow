package br.com.bankflow.transfer.clients.balance;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateBalanceHoldRequest(
		@JsonProperty("transfer_id") String transferId,
		@JsonProperty("account_id") long accountId,
		@JsonProperty("amount_minor") long amountMinor,
		String currency,
		String reason,
		@JsonProperty("expires_at") long expiresAt
) {
}

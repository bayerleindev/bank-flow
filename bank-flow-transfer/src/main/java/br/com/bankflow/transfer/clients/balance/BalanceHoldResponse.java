package br.com.bankflow.transfer.clients.balance;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BalanceHoldResponse(
		@JsonProperty("hold_id") String holdId,
		@JsonProperty("transfer_id") String transferId,
		@JsonProperty("account_id") long accountId,
		@JsonProperty("amount_minor") long amountMinor,
		String currency,
		String status,
		String reason,
		@JsonProperty("expires_at") long expiresAt,
		@JsonProperty("created_at") long createdAt,
		@JsonProperty("updated_at") long updatedAt
) {
}

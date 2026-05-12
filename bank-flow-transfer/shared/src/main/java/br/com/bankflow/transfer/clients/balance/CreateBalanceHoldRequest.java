package br.com.bankflow.transfer.clients.balance;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record CreateBalanceHoldRequest(
		@JsonProperty("transfer_id") String transferId,
		@JsonProperty("digital_account_id") UUID digitalAccountId,
		@JsonProperty("amount_minor") long amountMinor,
		String currency,
		String reason,
		@JsonProperty("expires_at") long expiresAt
) {
}

package br.com.bankflow.balance.controllers.dtos;

import br.com.bankflow.balance.domain.AccountHold;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AccountHoldResponse(
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
	public static AccountHoldResponse from(AccountHold hold) {
		return new AccountHoldResponse(
				hold.holdId(),
				hold.transferId(),
				hold.accountId(),
				hold.amountMinor(),
				hold.currency(),
				hold.status().name(),
				hold.reason(),
				hold.expiresAt(),
				hold.createdAt(),
				hold.updatedAt()
		);
	}
}

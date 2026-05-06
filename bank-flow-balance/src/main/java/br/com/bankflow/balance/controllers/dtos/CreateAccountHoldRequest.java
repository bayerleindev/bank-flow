package br.com.bankflow.balance.controllers.dtos;

import br.com.bankflow.balance.domain.CreateAccountHoldCommand;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateAccountHoldRequest(
		@JsonProperty("transfer_id") String transferId,
		@JsonProperty("account_id") long accountId,
		@JsonProperty("amount_minor") long amountMinor,
		String currency,
		String reason,
		@JsonProperty("expires_at") long expiresAt
) {
	public CreateAccountHoldCommand toCommand() {
		return new CreateAccountHoldCommand(
				transferId,
				accountId,
				amountMinor,
				currency,
				reason,
				expiresAt
		);
	}
}

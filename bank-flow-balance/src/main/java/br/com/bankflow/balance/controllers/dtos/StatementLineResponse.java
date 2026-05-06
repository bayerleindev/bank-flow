package br.com.bankflow.balance.controllers.dtos;

import br.com.bankflow.balance.domain.AccountStatementLine;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StatementLineResponse(
		@JsonProperty("line_id") long lineId,
		@JsonProperty("entry_id") long entryId,
		@JsonProperty("account_id") long accountId,
		@JsonProperty("external_id") String externalId,
		@JsonProperty("entry_type") String entryType,
		String direction,
		@JsonProperty("amount_minor") long amountMinor,
		@JsonProperty("signed_amount_minor") long signedAmountMinor,
		String currency,
		String description,
		@JsonProperty("occurred_at") long occurredAt,
		@JsonProperty("created_at") long createdAt
) {
	public static StatementLineResponse from(AccountStatementLine line) {
		return new StatementLineResponse(
				line.lineId(),
				line.entryId(),
				line.accountId(),
				line.externalId(),
				line.entryType(),
				line.direction(),
				line.amountMinor(),
				line.signedAmountMinor(),
				line.currency(),
				line.description(),
				line.occurredAt(),
				line.createdAt()
		);
	}
}

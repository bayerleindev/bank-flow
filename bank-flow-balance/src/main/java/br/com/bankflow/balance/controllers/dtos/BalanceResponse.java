package br.com.bankflow.balance.controllers.dtos;

import br.com.bankflow.balance.domain.AccountBalance;
import com.fasterxml.jackson.annotation.JsonProperty;

public record BalanceResponse(
		@JsonProperty("account_id") long accountId,
		String currency,
		@JsonProperty("posted_minor") long postedMinor,
		@JsonProperty("updated_at") long updatedAt
) {
	public static BalanceResponse from(AccountBalance balance) {
		return new BalanceResponse(
				balance.accountId(),
				balance.currency(),
				balance.postedMinor(),
				balance.updatedAt()
		);
	}
}

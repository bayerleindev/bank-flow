package br.com.bankflow.balance.controllers.dtos;

import br.com.bankflow.balance.services.BalanceQueryService;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record StatementResponse(
		BalanceResponse balance,
		List<StatementLineResponse> lines,
		int limit,
		@JsonProperty("next_cursor") String nextCursor
) {
	public static StatementResponse from(BalanceQueryService.AccountStatement statement) {
		return new StatementResponse(
				BalanceResponse.from(statement.balance()),
				statement.lines().stream()
						.map(StatementLineResponse::from)
						.toList(),
				statement.limit(),
				statement.nextCursor()
		);
	}
}

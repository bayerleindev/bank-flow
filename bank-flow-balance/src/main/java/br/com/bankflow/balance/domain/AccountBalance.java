package br.com.bankflow.balance.domain;

public record AccountBalance(
		long accountId,
		String currency,
		long postedMinor,
		long updatedAt
) {
}

package br.com.bankflow.balance.domain;

public record AccountBalance(
		java.util.UUID digitalAccountId,
		String currency,
		long postedMinor,
		long heldMinor,
		long updatedAt
) {
	public long availableMinor() {
		return postedMinor - heldMinor;
	}
}

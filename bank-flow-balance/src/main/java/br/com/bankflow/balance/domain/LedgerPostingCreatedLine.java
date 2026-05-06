package br.com.bankflow.balance.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LedgerPostingCreatedLine(
		@JsonProperty("line_id") long lineId,
		@JsonProperty("entry_id") long entryId,
		@JsonProperty("account_id") long accountId,
		String direction,
		@JsonProperty("amount_minor") long amountMinor,
		@JsonProperty("signed_amount_minor") long signedAmountMinor,
		String currency,
		@JsonProperty("line_memo") String lineMemo,
		@JsonProperty("created_at") long createdAt
) {
	public void validate() {
		if (lineId <= 0) {
			throw new IllegalArgumentException("line_id must be positive");
		}
		if (entryId <= 0) {
			throw new IllegalArgumentException("entry_id must be positive");
		}
		if (accountId <= 0) {
			throw new IllegalArgumentException("account_id must be positive");
		}
		if (!"DEBIT".equals(direction) && !"CREDIT".equals(direction)) {
			throw new IllegalArgumentException("direction must be DEBIT or CREDIT");
		}
		if (amountMinor <= 0) {
			throw new IllegalArgumentException("amount_minor must be positive");
		}
		if ("DEBIT".equals(direction) && signedAmountMinor >= 0) {
			throw new IllegalArgumentException("debit signed_amount_minor must be negative");
		}
		if ("CREDIT".equals(direction) && signedAmountMinor <= 0) {
			throw new IllegalArgumentException("credit signed_amount_minor must be positive");
		}
		if (Math.abs(signedAmountMinor) != amountMinor) {
			throw new IllegalArgumentException("signed_amount_minor must match amount_minor");
		}
		if (currency == null || currency.length() != 3) {
			throw new IllegalArgumentException("currency must be a 3-letter code");
		}
		if (lineMemo == null || lineMemo.isBlank()) {
			throw new IllegalArgumentException("line_memo is required");
		}
		if (createdAt <= 0) {
			throw new IllegalArgumentException("created_at must be positive");
		}
	}
}

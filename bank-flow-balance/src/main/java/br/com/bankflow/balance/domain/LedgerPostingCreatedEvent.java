package br.com.bankflow.balance.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record LedgerPostingCreatedEvent(
		@JsonProperty("entry_id") long entryId,
		@JsonProperty("external_id") String externalId,
		@JsonProperty("entry_type") String entryType,
		String status,
		String description,
		@JsonProperty("occurred_at") long occurredAt,
		@JsonProperty("created_at") long createdAt,
		@JsonProperty("reversal_of_entry_id") long reversalOfEntryId,
		String metadata,
		List<LedgerPostingCreatedLine> lines
) {
	public void validate() {
		if (entryId <= 0) {
			throw new IllegalArgumentException("entry_id must be positive");
		}
		if (isBlank(externalId)) {
			throw new IllegalArgumentException("external_id is required");
		}
		if (isBlank(entryType)) {
			throw new IllegalArgumentException("entry_type is required");
		}
		if (isBlank(status)) {
			throw new IllegalArgumentException("status is required");
		}
		if (!"POSTED".equals(status)) {
			throw new IllegalArgumentException("status must be POSTED");
		}
		if (isBlank(description)) {
			throw new IllegalArgumentException("description is required");
		}
		if (occurredAt <= 0) {
			throw new IllegalArgumentException("occurred_at must be positive");
		}
		if (createdAt <= 0) {
			throw new IllegalArgumentException("created_at must be positive");
		}
		if (lines == null || lines.size() < 2) {
			throw new IllegalArgumentException("at least two lines are required");
		}
		lines.forEach(LedgerPostingCreatedLine::validate);
		validateBalancedByCurrency();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private void validateBalancedByCurrency() {
		Map<String, Long> balancesByCurrency = new HashMap<>();
		for (LedgerPostingCreatedLine line : lines) {
			balancesByCurrency.merge(line.currency(), line.signedAmountMinor(), Long::sum);
		}
		boolean balanced = balancesByCurrency.values().stream().allMatch(total -> total == 0);
		if (!balanced) {
			throw new IllegalArgumentException("posting lines must balance to zero by currency");
		}
	}
}

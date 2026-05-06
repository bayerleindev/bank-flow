package br.com.bankflow.ledger.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record LedgerReversalRequestedEvent(
		@JsonProperty("reversal_id") UUID reversalId,
		@JsonProperty("original_external_id") String originalExternalId,
		String reason
) {
	public void validate() {
		if (reversalId == null) {
			throw new IllegalArgumentException("reversal_id is required");
		}
		if (originalExternalId == null || originalExternalId.isBlank()) {
			throw new IllegalArgumentException("original_external_id is required");
		}
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("reason is required");
		}
	}
}

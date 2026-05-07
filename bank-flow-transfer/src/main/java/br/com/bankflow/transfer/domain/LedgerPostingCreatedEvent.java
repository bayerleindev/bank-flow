package br.com.bankflow.transfer.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerPostingCreatedEvent(
		@JsonProperty("entry_id") long entryId,
		@JsonProperty("external_id") String externalId,
		@JsonProperty("entry_type") String entryType,
		String status
) {
	public void validate() {
		if (entryId <= 0) {
			throw new IllegalArgumentException("entry_id must be positive");
		}
		if (externalId == null || externalId.isBlank()) {
			throw new IllegalArgumentException("external_id is required");
		}
		if (entryType == null || entryType.isBlank()) {
			throw new IllegalArgumentException("entry_type is required");
		}
		if (!"POSTED".equals(status)) {
			throw new IllegalArgumentException("status must be POSTED");
		}
	}

	public boolean isTransferPosting() {
		return "TRANSFER".equals(entryType);
	}
}

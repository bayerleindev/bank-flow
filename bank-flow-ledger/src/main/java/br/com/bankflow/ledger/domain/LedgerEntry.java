package br.com.bankflow.ledger.domain;

public record LedgerEntry(
		long entryId,
		String externalId,
		String entryType,
		String status,
		String description,
		long occurredAt,
		long createdAt,
		long reversalOfEntryId,
		String metadata
) {
	private static final String ENTRY_TYPE = "TRANSFER";
	private static final String REVERSAL_ENTRY_TYPE = "REVERSAL";
	private static final String STATUS = "POSTED";

	public static LedgerEntry from(
			long entryId,
			long occurredAt,
			long createdAt,
			long reversalOfEntryId,
			String metadata,
			TransferPostedEvent event
	) {
		event.validate();
		if (entryId <= 0) {
			throw new IllegalArgumentException("entry_id must be positive");
		}
		if (occurredAt <= 0) {
			throw new IllegalArgumentException("occurred_at must be positive");
		}
		if (createdAt <= 0) {
			throw new IllegalArgumentException("created_at must be positive");
		}
		if (metadata == null || metadata.isBlank()) {
			throw new IllegalArgumentException("metadata is required");
		}

		return new LedgerEntry(
				entryId,
				event.transferId().toString(),
				ENTRY_TYPE,
				STATUS,
				"Transferencia BRL de conta %s para conta %s".formatted(
						event.sourceAccount(),
						event.destinationAccount()
				),
				occurredAt,
				createdAt,
				reversalOfEntryId,
				metadata
		);
	}

	public static LedgerEntry reversal(
			long entryId,
			long occurredAt,
			long createdAt,
			long reversalOfEntryId,
			String externalId,
			String description,
			String metadata
	) {
		if (entryId <= 0) {
			throw new IllegalArgumentException("entry_id must be positive");
		}
		if (occurredAt <= 0) {
			throw new IllegalArgumentException("occurred_at must be positive");
		}
		if (createdAt <= 0) {
			throw new IllegalArgumentException("created_at must be positive");
		}
		if (reversalOfEntryId <= 0) {
			throw new IllegalArgumentException("reversal_of_entry_id must be positive");
		}
		if (externalId == null || externalId.isBlank()) {
			throw new IllegalArgumentException("external_id is required");
		}
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("description is required");
		}
		if (metadata == null || metadata.isBlank()) {
			throw new IllegalArgumentException("metadata is required");
		}

		return new LedgerEntry(
				entryId,
				externalId,
				REVERSAL_ENTRY_TYPE,
				STATUS,
				description,
				occurredAt,
				createdAt,
				reversalOfEntryId,
				metadata
		);
	}
}

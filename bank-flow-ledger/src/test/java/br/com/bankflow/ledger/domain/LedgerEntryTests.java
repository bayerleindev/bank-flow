package br.com.bankflow.ledger.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerEntryTests {
	@Test
	void createsLedgerEntryFromTransferEvent() {
		UUID transferId = UUID.randomUUID();
		TransferPostedEvent event = new TransferPostedEvent(
				transferId,
				UUID.randomUUID(),
				"12345-6",
				UUID.randomUUID(),
				"98765-4",
				1_500L,
				"BRL"
		);

		LedgerEntry entry = LedgerEntry.from(
				123L,
				1_778_012_345_000L,
				1_778_012_345_001L,
				0L,
				"{\"amount_cents\":1500}",
				event
		);

		assertEquals(123L, entry.entryId());
		assertEquals(transferId.toString(), entry.externalId());
		assertEquals("TRANSFER", entry.entryType());
		assertEquals("POSTED", entry.status());
		assertEquals("Transferencia BRL de conta 12345-6 para conta 98765-4", entry.description());
		assertEquals(1_778_012_345_000L, entry.occurredAt());
		assertEquals(1_778_012_345_001L, entry.createdAt());
		assertEquals(0L, entry.reversalOfEntryId());
		assertEquals("{\"amount_cents\":1500}", entry.metadata());
	}

	@Test
	void rejectsInvalidEntryId() {
		TransferPostedEvent event = new TransferPostedEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				"12345-6",
				UUID.randomUUID(),
				"98765-4",
				1_500L,
				"BRL"
		);

		assertThrows(IllegalArgumentException.class, () -> LedgerEntry.from(
				0L,
				1_778_012_345_000L,
				1_778_012_345_001L,
				0L,
				"{\"amount_cents\":1500}",
				event
		));
	}
}

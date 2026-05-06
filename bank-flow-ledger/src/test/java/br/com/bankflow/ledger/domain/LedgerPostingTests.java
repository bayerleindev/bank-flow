package br.com.bankflow.ledger.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerPostingTests {
	@Test
	void createsBalancedPosting() {
		LedgerEntry entry = entry();
		LedgerEntryLine debitLine = LedgerEntryLine.debit(11L, entry.entryId(), 101L, 1_500L, "BRL", "Debito", 1L);
		LedgerEntryLine creditLine = LedgerEntryLine.credit(12L, entry.entryId(), 102L, 1_500L, "BRL", "Credito", 1L);

		LedgerPosting posting = LedgerPosting.of(entry, List.of(debitLine, creditLine));

		assertEquals(entry, posting.entry());
		assertEquals(2, posting.lines().size());
	}

	@Test
	void rejectsUnbalancedPosting() {
		LedgerEntry entry = entry();
		LedgerEntryLine debitLine = LedgerEntryLine.debit(11L, entry.entryId(), 101L, 1_500L, "BRL", "Debito", 1L);
		LedgerEntryLine creditLine = LedgerEntryLine.credit(12L, entry.entryId(), 102L, 1_400L, "BRL", "Credito", 1L);

		assertThrows(IllegalArgumentException.class, () -> LedgerPosting.of(entry, List.of(debitLine, creditLine)));
	}

	@Test
	void rejectsPostingWithDifferentCurrencies() {
		LedgerEntry entry = entry();
		LedgerEntryLine debitLine = LedgerEntryLine.debit(11L, entry.entryId(), 101L, 1_500L, "BRL", "Debito", 1L);
		LedgerEntryLine creditLine = LedgerEntryLine.credit(12L, entry.entryId(), 102L, 1_500L, "USD", "Credito", 1L);

		assertThrows(IllegalArgumentException.class, () -> LedgerPosting.of(entry, List.of(debitLine, creditLine)));
	}

	@Test
	void rejectsPostingWithLineFromAnotherEntry() {
		LedgerEntry entry = entry();
		LedgerEntryLine debitLine = LedgerEntryLine.debit(11L, entry.entryId(), 101L, 1_500L, "BRL", "Debito", 1L);
		LedgerEntryLine creditLine = LedgerEntryLine.credit(12L, 999L, 102L, 1_500L, "BRL", "Credito", 1L);

		assertThrows(IllegalArgumentException.class, () -> LedgerPosting.of(entry, List.of(debitLine, creditLine)));
	}

	@Test
	void rejectsInvalidDirection() {
		LedgerEntry entry = entry();
		LedgerEntryLine debitLine = LedgerEntryLine.debit(11L, entry.entryId(), 101L, 1_500L, "BRL", "Debito", 1L);
		LedgerEntryLine invalidLine = new LedgerEntryLine(
				12L,
				entry.entryId(),
				102L,
				"LEFT",
				1_500L,
				1_500L,
				"BRL",
				"Credito",
				1L
		);

		assertThrows(IllegalArgumentException.class, () -> LedgerPosting.of(entry, List.of(debitLine, invalidLine)));
	}

	private LedgerEntry entry() {
		TransferPostedEvent event = new TransferPostedEvent(
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872bf"),
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872b1"),
				"12345-6",
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872b2"),
				"98765-4",
				1_500L,
				"BRL"
		);
		return LedgerEntry.from(22L, 1L, 1L, 0L, "{}", event);
	}
}

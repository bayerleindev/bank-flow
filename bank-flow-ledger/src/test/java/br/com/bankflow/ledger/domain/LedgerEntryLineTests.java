package br.com.bankflow.ledger.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerEntryLineTests {
	@Test
	void createsDebitLine() {
		LedgerEntryLine line = LedgerEntryLine.debit(
				11L,
				22L,
				33L,
				1_500L,
				"BRL",
				"Debito transferencia",
				1_778_012_345_000L
		);

		assertEquals(11L, line.lineId());
		assertEquals(22L, line.entryId());
		assertEquals(33L, line.accountId());
		assertEquals("DEBIT", line.direction());
		assertEquals(1_500L, line.amountMinor());
		assertEquals(-1_500L, line.signedAmountMinor());
		assertEquals("BRL", line.currency());
		assertEquals("Debito transferencia", line.lineMemo());
		assertEquals(1_778_012_345_000L, line.createdAt());
	}

	@Test
	void createsCreditLine() {
		LedgerEntryLine line = LedgerEntryLine.credit(
				11L,
				22L,
				33L,
				1_500L,
				"BRL",
				"Credito transferencia",
				1_778_012_345_000L
		);

		assertEquals("CREDIT", line.direction());
		assertEquals(1_500L, line.signedAmountMinor());
	}

	@Test
	void rejectsInvalidAmount() {
		assertThrows(IllegalArgumentException.class, () -> LedgerEntryLine.debit(
				11L,
				22L,
				33L,
				0L,
				"BRL",
				"Debito transferencia",
				1_778_012_345_000L
		));
	}
}

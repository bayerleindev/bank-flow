package br.com.bankflow.ledger.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerAccountTests {
	@Test
	void createsLedgerAccountFromAccountCreatedEvent() {
		UUID ownerId = UUID.randomUUID();
		AccountCreatedEvent event = new AccountCreatedEvent(ownerId, "0001", "12345-6", "BRL");

		LedgerAccount account = LedgerAccount.from(123L, 1_778_012_345_000L, event);

		assertEquals(123L, account.accountId());
		assertEquals("CUSTOMER_ACCOUNT_%s".formatted(ownerId), account.accountCode());
		assertEquals("Saldo dispon\u00edvel BRL - Conta 12345-6", account.accountName());
		assertEquals("LIABILITY", account.accountType());
		assertEquals("CREDIT", account.normalBalance());
		assertEquals("BRL", account.currency());
		assertEquals("BANK_ACCOUNT", account.ownerType());
		assertEquals(ownerId, account.digitalAccountId());
		assertTrue(account.active());
		assertEquals(1_778_012_345_000L, account.createdAt());
	}

	@Test
	void rejectsNonBrlAccountCreatedEvent() {
		AccountCreatedEvent event = new AccountCreatedEvent(UUID.randomUUID(), "0001", "12345-6", "USD");

		assertThrows(IllegalArgumentException.class, () -> LedgerAccount.from(123L, 1_778_012_345_000L, event));
	}

	@Test
	void rejectsInvalidAccountId() {
		AccountCreatedEvent event = new AccountCreatedEvent(UUID.randomUUID(), "0001", "12345-6", "BRL");

		assertThrows(IllegalArgumentException.class, () -> LedgerAccount.from(0L, 1_778_012_345_000L, event));
	}

	@Test
	void rejectsInvalidCreatedAt() {
		AccountCreatedEvent event = new AccountCreatedEvent(UUID.randomUUID(), "0001", "12345-6", "BRL");

		assertThrows(IllegalArgumentException.class, () -> LedgerAccount.from(123L, 0L, event));
	}
}

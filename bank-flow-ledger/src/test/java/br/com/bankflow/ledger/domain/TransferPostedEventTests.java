package br.com.bankflow.ledger.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferPostedEventTests {
	@Test
	void validatesRequiredFields() {
		TransferPostedEvent event = new TransferPostedEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				"12345-6",
				UUID.randomUUID(),
				"98765-4",
				1_500L,
				"BRL"
		);

		assertDoesNotThrow(event::validate);
	}

	@Test
	void rejectsInvalidAmount() {
		TransferPostedEvent event = new TransferPostedEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				"12345-6",
				UUID.randomUUID(),
				"98765-4",
				0L,
				"BRL"
		);

		assertThrows(IllegalArgumentException.class, event::validate);
	}
}

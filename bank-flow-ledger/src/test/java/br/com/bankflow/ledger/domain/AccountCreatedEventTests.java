package br.com.bankflow.ledger.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountCreatedEventTests {
	@Test
	void validatesRequiredFields() {
		AccountCreatedEvent event = new AccountCreatedEvent(
				UUID.randomUUID(),
				"0001",
				"12345-6",
				"BRL"
		);

		assertDoesNotThrow(event::validate);
	}

	@Test
	void rejectsMissingOwnerId() {
		AccountCreatedEvent event = new AccountCreatedEvent(null, "0001", "12345-6", "BRL");

		assertThrows(IllegalArgumentException.class, event::validate);
	}
}

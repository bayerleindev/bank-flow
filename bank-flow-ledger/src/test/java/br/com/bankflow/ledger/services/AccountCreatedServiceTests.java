package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.AccountCreatedEvent;
import br.com.bankflow.ledger.domain.LedgerAccount;
import br.com.bankflow.ledger.repositories.LedgerAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountCreatedServiceTests {
	@Test
	void createsLedgerAccountWhenAccountCodeWasNotProcessed() {
		InMemoryLedgerAccountRepository repository = new InMemoryLedgerAccountRepository(true);
		AccountCreatedService service = newService(repository);

		service.createLedgerAccount(event());

		assertEquals(1, repository.calls);
		assertEquals("CUSTOMER_ACCOUNT_018f6e4f-f427-7c32-9d4b-3bc9e72872b1", repository.account.accountCode());
	}

	@Test
	void treatsDuplicateAccountCodeAsAlreadyProcessed() {
		InMemoryLedgerAccountRepository repository = new InMemoryLedgerAccountRepository(false);
		AccountCreatedService service = newService(repository);

		service.createLedgerAccount(event());

		assertEquals(1, repository.calls);
	}

	private AccountCreatedService newService(InMemoryLedgerAccountRepository repository) {
		return new AccountCreatedService(
				repository,
				new NumericIdGenerator(Clock.fixed(Instant.parse("2026-05-05T20:00:00Z"), ZoneOffset.UTC)),
				Clock.fixed(Instant.parse("2026-05-05T20:00:00Z"), ZoneOffset.UTC)
		);
	}

	private AccountCreatedEvent event() {
		return new AccountCreatedEvent(
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872b1"),
				"0001",
				"12345-6",
				"BRL"
		);
	}

	private static class InMemoryLedgerAccountRepository implements LedgerAccountRepository {
		private final boolean created;
		private int calls;
		private LedgerAccount account;

		private InMemoryLedgerAccountRepository(boolean created) {
			this.created = created;
		}

		@Override
		public boolean saveIfNotExists(LedgerAccount account) {
			this.calls++;
			this.account = account;
			return created;
		}

		@Override
		public OptionalLong findAccountIdByOwnerId(UUID ownerId) {
			return OptionalLong.empty();
		}
	}
}

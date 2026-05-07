package br.com.bankflow.accounts.services;

import br.com.bankflow.accounts.clients.baas.BaasAccountResponse;
import br.com.bankflow.accounts.clients.baas.BaasAccountStatus;
import br.com.bankflow.accounts.clients.baas.BaasClient;
import br.com.bankflow.accounts.domain.Account;
import br.com.bankflow.accounts.domain.AccountStatus;
import br.com.bankflow.accounts.domain.CreateAccountCommand;
import br.com.bankflow.accounts.domain.OutboxEvent;
import br.com.bankflow.accounts.repositories.AccountRepository;
import br.com.bankflow.accounts.repositories.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountCreationServiceTests {
	private final FakeAccountRepository accountRepository = new FakeAccountRepository();
	private final FakeOutboxEventRepository outboxRepository = new FakeOutboxEventRepository();
	private final FakeBaasClient baasClient = new FakeBaasClient();
	private final AccountCreationService service = new AccountCreationService(
			accountRepository,
			outboxRepository,
			baasClient,
			new ObjectMapper(),
			Clock.fixed(Instant.ofEpochMilli(1_778_100_000_000L), ZoneOffset.UTC),
			"account-created"
	);

	@Test
	void createsActiveAccountUsingBranchAndAccountReturnedByBaas() {
		Account account = service.createAccount(command("idem-1", "35225454860"));

		assertEquals(AccountStatus.ACTIVE, account.status());
		assertEquals("0001", account.branch());
		assertEquals("54860-0", account.account());
		assertEquals("baas-35225454860", account.baasAccountId());
		assertEquals(1, baasClient.calls);
		assertEquals(1, outboxRepository.events.size());
		assertEquals("account-created", outboxRepository.events.getFirst().topic());
		assertEquals(account.accountId().toString(), outboxRepository.events.getFirst().eventKey());
		assertTrue(outboxRepository.events.getFirst().payload().contains("\"branch\":\"0001\""));
	}

	@Test
	void returnsExistingAccountForSameIdempotencyKey() {
		Account first = service.createAccount(command("idem-1", "35225454860"));
		Account duplicate = service.createAccount(command("idem-1", "35225454860"));

		assertEquals(first.accountId(), duplicate.accountId());
		assertEquals(1, baasClient.calls);
		assertEquals(1, outboxRepository.events.size());
	}

	@Test
	void returnsExistingAccountForSameDocumentNumber() {
		Account first = service.createAccount(command("idem-1", "35225454860"));
		Account duplicate = service.createAccount(command("idem-2", "352.254.548-60"));

		assertEquals(first.accountId(), duplicate.accountId());
		assertEquals(1, baasClient.calls);
		assertEquals(1, outboxRepository.events.size());
	}

	private CreateAccountCommand command(String idempotencyKey, String documentNumber) {
		return new CreateAccountCommand(
				idempotencyKey,
				"Maria Silva",
				documentNumber,
				"maria@example.com",
				"Ana Silva",
				"Maria",
				"+5511999999999",
				LocalDate.of(1996, 12, 18),
				"Rua Teste, 123",
				false
		);
	}

	private static class FakeBaasClient implements BaasClient {
		private int calls;

		@Override
		public BaasAccountResponse createAccount(CreateAccountCommand command) {
			calls++;
			String document = command.documentNumber().replaceAll("\\D", "");
			return new BaasAccountResponse(
					"baas-" + document,
					"0001",
					document.substring(document.length() - 5) + "-0",
					"BRL",
					BaasAccountStatus.ACTIVE,
					null
			);
		}
	}

	private static class FakeAccountRepository implements AccountRepository {
		private final Map<UUID, Account> byId = new HashMap<>();
		private final Map<String, UUID> byIdempotencyKey = new HashMap<>();
		private final Map<String, UUID> byDocument = new HashMap<>();

		@Override
		public Optional<Account> findByIdempotencyKey(String idempotencyKey) {
			return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey)).map(byId::get);
		}

		@Override
		public Optional<Account> findByDocumentNumber(String documentNumber) {
			return Optional.ofNullable(byDocument.get(normalize(documentNumber))).map(byId::get);
		}

		@Override
		public Optional<Account> findByAccountId(UUID accountId) {
			return Optional.ofNullable(byId.get(accountId));
		}

		@Override
		public long countByStatus(AccountStatus status) {
			return byId.values().stream()
					.filter(account -> account.status() == status)
					.count();
		}

		@Override
		public OptionalLong oldestUpdatedAtByStatus(AccountStatus status) {
			return byId.values().stream()
					.filter(account -> account.status() == status)
					.mapToLong(Account::updatedAt)
					.min();
		}

		@Override
		public Account create(UUID accountId, CreateAccountCommand command, long now) {
			Account account = new Account(
					accountId,
					command.idempotencyKey(),
					command.fullName(),
					normalize(command.documentNumber()),
					command.email(),
					command.motherName(),
					command.socialName(),
					command.phoneNumber(),
					command.birthDate(),
					command.address(),
					command.politicallyExposed(),
					null,
					null,
					null,
					"BRL",
					AccountStatus.RECEIVED,
					null,
					now,
					now
			);
			byId.put(accountId, account);
			byIdempotencyKey.put(command.idempotencyKey(), accountId);
			byDocument.put(normalize(command.documentNumber()), accountId);
			return account;
		}

		@Override
		public Account updateFromBaas(
				UUID accountId,
				BaasAccountResponse response,
				AccountStatus status,
				String failureReason,
				long now
		) {
			Account current = byId.get(accountId);
			Account updated = new Account(
					current.accountId(),
					current.idempotencyKey(),
					current.fullName(),
					current.documentNumber(),
					current.email(),
					current.motherName(),
					current.socialName(),
					current.phoneNumber(),
					current.birthDate(),
					current.address(),
					current.politicallyExposed(),
					response.baasAccountId(),
					response.branch(),
					response.account(),
					response.currency(),
					status,
					failureReason,
					current.createdAt(),
					now
			);
			byId.put(accountId, updated);
			return updated;
		}

		private String normalize(String documentNumber) {
			return documentNumber.replaceAll("\\D", "");
		}
	}

	private static class FakeOutboxEventRepository implements OutboxEventRepository {
		private final List<OutboxEvent> events = new ArrayList<>();

		@Override
		public void createIfAbsent(OutboxEvent event) {
			boolean exists = events.stream()
					.anyMatch(existing -> existing.aggregateType().equals(event.aggregateType())
							&& existing.aggregateId().equals(event.aggregateId())
							&& existing.eventType().equals(event.eventType()));
			if (!exists) {
				events.add(event);
			}
		}

		@Override
		public List<OutboxEvent> findPending(int limit) {
			return events.stream().limit(limit).toList();
		}

		@Override
		public void markPublished(UUID eventId, long publishedAt) {
		}

		@Override
		public void markFailed(UUID eventId, String errorMessage) {
		}
	}
}

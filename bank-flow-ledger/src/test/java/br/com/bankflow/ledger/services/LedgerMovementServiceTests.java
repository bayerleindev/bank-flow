package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.LedgerAccount;
import br.com.bankflow.ledger.domain.LedgerEntryLine;
import br.com.bankflow.ledger.domain.LedgerPosting;
import br.com.bankflow.ledger.domain.TransferPostedEvent;
import br.com.bankflow.ledger.repositories.LedgerAccountRepository;
import br.com.bankflow.ledger.repositories.LedgerPostingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerMovementServiceTests {
	private static final UUID SOURCE_DIGITAL_ACCOUNT_ID = UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872b1");
	private static final UUID DESTINATION_DIGITAL_ACCOUNT_ID = UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872b2");
	private static final UUID EXTERNAL_INBOUND_SETTLEMENT_DIGITAL_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

	@Test
	void createsExactlyOneEntryAndTwoBalancedLinesUsingLedgerAccountIds() throws Exception {
		InMemoryLedgerPostingRepository postingRepository = new InMemoryLedgerPostingRepository();
		CapturingLedgerPostingPublisher publisher = new CapturingLedgerPostingPublisher();
		LedgerMovementService service = newService(new FixedLedgerAccountRepository(), postingRepository, publisher);

		service.postTransfer(transferEvent());

		assertEquals(1, postingRepository.calls);
		assertEquals(1, postingRepository.savedPostings.size());

		LedgerPosting posting = postingRepository.savedPostings.getFirst();
		assertNotNull(posting.entry());
		assertEquals(transferEvent().transferId().toString(), posting.entry().externalId());
		assertEquals(2, posting.lines().size());
		assertEquals(10_001L, posting.lines().get(0).accountId());
		assertEquals("DEBIT", posting.lines().get(0).direction());
		assertEquals(10_002L, posting.lines().get(1).accountId());
		assertEquals("CREDIT", posting.lines().get(1).direction());
		assertEquals(0L, posting.lines().stream().mapToLong(LedgerEntryLine::signedAmountMinor).sum());
		assertEquals(1, publisher.calls);
		assertEquals(posting, publisher.publishedPostings.getFirst());
	}

	@Test
	void failsWhenSourceAccountDoesNotExist() {
		InMemoryLedgerPostingRepository postingRepository = new InMemoryLedgerPostingRepository();
		LedgerMovementService service = newService(
				new FixedLedgerAccountRepository(OptionalLong.empty(), OptionalLong.of(10_002L)),
				postingRepository,
				new CapturingLedgerPostingPublisher()
		);

		assertThrows(IllegalArgumentException.class, () -> service.postTransfer(transferEvent()));

		assertEquals(0, postingRepository.calls);
		assertEquals(0, postingRepository.savedPostings.size());
	}

	@Test
	void failsWhenDestinationAccountDoesNotExist() {
		InMemoryLedgerPostingRepository postingRepository = new InMemoryLedgerPostingRepository();
		LedgerMovementService service = newService(
				new FixedLedgerAccountRepository(OptionalLong.of(10_001L), OptionalLong.empty()),
				postingRepository,
				new CapturingLedgerPostingPublisher()
		);

		assertThrows(IllegalArgumentException.class, () -> service.postTransfer(transferEvent()));

		assertEquals(0, postingRepository.calls);
		assertEquals(0, postingRepository.savedPostings.size());
	}

	@Test
	void doesNotPersistDuplicatePostingWhenSameTransferIdIsProcessedAgain() throws Exception {
		InMemoryLedgerPostingRepository postingRepository = new InMemoryLedgerPostingRepository();
		CapturingLedgerPostingPublisher publisher = new CapturingLedgerPostingPublisher();
		LedgerMovementService service = newService(new FixedLedgerAccountRepository(), postingRepository, publisher);

		service.postTransfer(transferEvent());
		service.postTransfer(transferEvent());

		assertEquals(2, postingRepository.calls);
		assertEquals(1, postingRepository.savedPostings.size());
		assertEquals(transferEvent().transferId().toString(), postingRepository.savedPostings.getFirst().entry().externalId());
		assertEquals(2, publisher.calls);
		assertEquals(postingRepository.savedPostings.getFirst(), publisher.publishedPostings.getLast());
	}

	@Test
	void postsExternalInboundTransferFromSettlementAccountToCustomerAccount() throws Exception {
		InMemoryLedgerPostingRepository postingRepository = new InMemoryLedgerPostingRepository();
		LedgerMovementService service = newService(new FixedLedgerAccountRepository(), postingRepository, new CapturingLedgerPostingPublisher());

		service.postTransfer(externalInboundTransferEvent());

		LedgerPosting posting = postingRepository.savedPostings.getFirst();
		assertEquals(90_001L, posting.lines().get(0).accountId());
		assertEquals("DEBIT", posting.lines().get(0).direction());
		assertEquals(10_002L, posting.lines().get(1).accountId());
		assertEquals("CREDIT", posting.lines().get(1).direction());
		assertTrue(posting.entry().metadata().contains("\"debit_account_code\":\"SETTLEMENT_EXTERNAL_INBOUND_BRL\""));
	}

	private LedgerMovementService newService(
			FixedLedgerAccountRepository accountRepository,
			InMemoryLedgerPostingRepository postingRepository,
			CapturingLedgerPostingPublisher publisher
	) {
		return new LedgerMovementService(
				accountRepository,
				postingRepository,
				publisher,
				new NumericIdGenerator(Clock.fixed(Instant.parse("2026-05-05T20:00:00Z"), ZoneOffset.UTC)),
				Clock.fixed(Instant.parse("2026-05-05T20:00:00Z"), ZoneOffset.UTC),
				new ObjectMapper()
		);
	}

	private TransferPostedEvent transferEvent() {
		return new TransferPostedEvent(
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872bf"),
				SOURCE_DIGITAL_ACCOUNT_ID,
				"12345-6",
				DESTINATION_DIGITAL_ACCOUNT_ID,
				"98765-4",
				1_500L,
				"BRL"
		);
	}

	private TransferPostedEvent externalInboundTransferEvent() {
		return new TransferPostedEvent(
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872c0"),
				EXTERNAL_INBOUND_SETTLEMENT_DIGITAL_ACCOUNT_ID,
				"SETTLEMENT_EXTERNAL_INBOUND_BRL",
				DESTINATION_DIGITAL_ACCOUNT_ID,
				"98765-4",
				2_500L,
				"BRL"
		);
	}

	private static class FixedLedgerAccountRepository implements LedgerAccountRepository {
		private final OptionalLong sourceAccountId;
		private final OptionalLong destinationAccountId;

		private FixedLedgerAccountRepository() {
			this(OptionalLong.of(10_001L), OptionalLong.of(10_002L));
		}

		private FixedLedgerAccountRepository(OptionalLong sourceAccountId, OptionalLong destinationAccountId) {
			this.sourceAccountId = sourceAccountId;
			this.destinationAccountId = destinationAccountId;
		}

		@Override
		public boolean saveIfNotExists(LedgerAccount account) {
			return true;
		}

		@Override
		public OptionalLong findAccountIdByDigitalAccountId(UUID ownerId) {
			if (EXTERNAL_INBOUND_SETTLEMENT_DIGITAL_ACCOUNT_ID.equals(ownerId)) {
				return OptionalLong.of(90_001L);
			}
			if (SOURCE_DIGITAL_ACCOUNT_ID.equals(ownerId)) {
				return sourceAccountId;
			}
			if (DESTINATION_DIGITAL_ACCOUNT_ID.equals(ownerId)) {
				return destinationAccountId;
			}
			return OptionalLong.empty();
		}
	}

	private static class InMemoryLedgerPostingRepository implements LedgerPostingRepository {
		private final Set<String> externalIds = new HashSet<>();
		private final List<LedgerPosting> savedPostings = new ArrayList<>();
		private int calls;

		@Override
		public boolean saveIfNotExists(LedgerPosting posting) {
			this.calls++;
			if (!externalIds.add(posting.entry().externalId())) {
				return false;
			}
			savedPostings.add(posting);
			return true;
		}

		@Override
		public Optional<LedgerPosting> findByExternalId(String externalId) {
			return savedPostings.stream()
					.filter(posting -> posting.entry().externalId().equals(externalId))
					.findFirst();
		}

		@Override
		public boolean reversalExistsFor(long entryId) {
			return savedPostings.stream()
					.anyMatch(posting -> posting.entry().reversalOfEntryId() == entryId);
		}
	}

	private static class CapturingLedgerPostingPublisher implements LedgerPostingPublisher {
		private final List<LedgerPosting> publishedPostings = new ArrayList<>();
		private int calls;

		@Override
		public void publish(LedgerPosting posting) {
			this.calls++;
			this.publishedPostings.add(posting);
		}
	}
}

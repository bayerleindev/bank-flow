package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.LedgerEntry;
import br.com.bankflow.ledger.domain.LedgerEntryLine;
import br.com.bankflow.ledger.domain.LedgerPosting;
import br.com.bankflow.ledger.domain.LedgerReversalRequestedEvent;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerReversalServiceTests {
	@Test
	void createsReversalPostingWithInvertedLines() throws Exception {
		InMemoryLedgerPostingRepository repository = new InMemoryLedgerPostingRepository(originalPosting());
		CapturingLedgerPostingPublisher publisher = new CapturingLedgerPostingPublisher();
		LedgerReversalService service = newService(repository, publisher);

		service.reverse(event());

		LedgerPosting reversalPosting = repository.savedPostings.get(1);
		assertEquals("REVERSAL", reversalPosting.entry().entryType());
		assertEquals(10L, reversalPosting.entry().reversalOfEntryId());
		assertEquals(event().reversalId().toString(), reversalPosting.entry().externalId());
		assertEquals("CREDIT", reversalPosting.lines().get(0).direction());
		assertEquals(1_500L, reversalPosting.lines().get(0).signedAmountMinor());
		assertEquals("DEBIT", reversalPosting.lines().get(1).direction());
		assertEquals(-1_500L, reversalPosting.lines().get(1).signedAmountMinor());
		assertEquals(0L, reversalPosting.lines().stream().mapToLong(LedgerEntryLine::signedAmountMinor).sum());
		assertEquals(1, publisher.calls);
		assertEquals(reversalPosting, publisher.publishedPostings.getFirst());
	}

	@Test
	void treatsSameReversalIdAsAlreadyProcessed() throws Exception {
		LedgerPosting originalPosting = originalPosting();
		LedgerPosting existingReversal = reversalPosting(originalPosting.entry());
		InMemoryLedgerPostingRepository repository = new InMemoryLedgerPostingRepository(originalPosting, existingReversal);
		CapturingLedgerPostingPublisher publisher = new CapturingLedgerPostingPublisher();
		LedgerReversalService service = newService(repository, publisher);

		service.reverse(event());

		assertEquals(2, repository.savedPostings.size());
		assertEquals(0, publisher.calls);
	}

	@Test
	void rejectsSecondReversalWithDifferentReversalId() {
		LedgerPosting originalPosting = originalPosting();
		LedgerPosting existingReversal = reversalPosting(originalPosting.entry());
		InMemoryLedgerPostingRepository repository = new InMemoryLedgerPostingRepository(originalPosting, existingReversal);
		LedgerReversalService service = newService(repository, new CapturingLedgerPostingPublisher());
		LedgerReversalRequestedEvent differentReversal = new LedgerReversalRequestedEvent(
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872c1"),
				"transfer-1",
				"TRANSFER_CANCELLED"
		);

		assertThrows(IllegalArgumentException.class, () -> service.reverse(differentReversal));
	}

	@Test
	void rejectsMissingOriginalPosting() {
		InMemoryLedgerPostingRepository repository = new InMemoryLedgerPostingRepository();
		LedgerReversalService service = newService(repository, new CapturingLedgerPostingPublisher());

		assertThrows(IllegalArgumentException.class, () -> service.reverse(event()));
	}

	private LedgerReversalService newService(
			InMemoryLedgerPostingRepository repository,
			CapturingLedgerPostingPublisher publisher
	) {
		return new LedgerReversalService(
				repository,
				publisher,
				new NumericIdGenerator(Clock.fixed(Instant.parse("2026-05-05T20:00:00Z"), ZoneOffset.UTC)),
				Clock.fixed(Instant.parse("2026-05-05T20:00:00Z"), ZoneOffset.UTC),
				new ObjectMapper()
		);
	}

	private LedgerReversalRequestedEvent event() {
		return new LedgerReversalRequestedEvent(
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872c0"),
				"transfer-1",
				"TRANSFER_CANCELLED"
		);
	}

	private LedgerPosting originalPosting() {
		LedgerEntry entry = new LedgerEntry(
				10L,
				"transfer-1",
				"TRANSFER",
				"POSTED",
				"Transferencia original",
				1L,
				1L,
				0L,
				"{}"
		);
		return LedgerPosting.of(entry, List.of(
				LedgerEntryLine.debit(11L, entry.entryId(), 101L, 1_500L, "BRL", "Debito", 1L),
				LedgerEntryLine.credit(12L, entry.entryId(), 102L, 1_500L, "BRL", "Credito", 1L)
		));
	}

	private LedgerPosting reversalPosting(LedgerEntry originalEntry) {
		LedgerEntry entry = LedgerEntry.reversal(
				20L,
				2L,
				2L,
				originalEntry.entryId(),
				event().reversalId().toString(),
				"Estorno",
				"{}"
		);
		return LedgerPosting.of(entry, List.of(
				LedgerEntryLine.credit(21L, entry.entryId(), 101L, 1_500L, "BRL", "Estorno", 2L),
				LedgerEntryLine.debit(22L, entry.entryId(), 102L, 1_500L, "BRL", "Estorno", 2L)
		));
	}

	private static class InMemoryLedgerPostingRepository implements LedgerPostingRepository {
		private final Set<String> externalIds = new HashSet<>();
		private final List<LedgerPosting> savedPostings = new ArrayList<>();

		private InMemoryLedgerPostingRepository(LedgerPosting... postings) {
			for (LedgerPosting posting : postings) {
				saveIfNotExists(posting);
			}
		}

		@Override
		public boolean saveIfNotExists(LedgerPosting posting) {
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

package br.com.bankflow.balance.services;

import br.com.bankflow.balance.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.balance.domain.LedgerPostingCreatedLine;
import br.com.bankflow.balance.observability.BalanceMetrics;
import br.com.bankflow.balance.repositories.BalanceProjectionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerPostingProjectionServiceTests {
	private final FakeBalanceProjectionRepository repository = new FakeBalanceProjectionRepository();
	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final LedgerPostingProjectionService service = new LedgerPostingProjectionService(
			repository,
			new BalanceMetrics(meterRegistry),
			Clock.fixed(Instant.ofEpochMilli(1_777_777_777_000L), ZoneOffset.UTC)
	);

	@Test
	void projectsPostingWithExactlyTwoBalancedLines() {
		LedgerPostingCreatedEvent event = validEvent();

		LedgerPostingProjectionResult result = service.project(event);

		assertEquals(LedgerPostingProjectionResult.PROJECTED, result);
		assertEquals(1, repository.markProcessedCalls);
		assertEquals(2, repository.savedLines.size());
		assertEquals(2, repository.appliedLines.size());
		assertEquals(0, repository.appliedLines.stream().mapToLong(LedgerPostingCreatedLine::signedAmountMinor).sum());
		assertEquals(List.of(1001L, 2002L), repository.appliedLines.stream()
				.map(LedgerPostingCreatedLine::accountId)
				.toList());
		assertEquals(1.0, counter("bank_flow_balance_projection_total", "result", "projected"));
	}

	@Test
	void ignoresAlreadyProcessedPosting() {
		repository.firstProcessing = false;

		LedgerPostingProjectionResult result = service.project(validEvent());

		assertEquals(LedgerPostingProjectionResult.DUPLICATE, result);
		assertEquals(1, repository.markProcessedCalls);
		assertEquals(0, repository.savedLines.size());
		assertEquals(0, repository.appliedLines.size());
		assertEquals(1.0, counter("bank_flow_balance_projection_total", "result", "duplicate"));
	}

	@Test
	void failsWhenLineDoesNotBelongToEntry() {
		LedgerPostingCreatedEvent event = new LedgerPostingCreatedEvent(
				10L,
				"transfer-1",
				"TRANSFER",
				"POSTED",
				"Transfer transfer-1",
				1_777_777_700L,
				1_777_777_710L,
				0L,
				"{}",
				List.of(
						new LedgerPostingCreatedLine(101L, 999L, 1001L, "DEBIT", 5000L, -5000L, "BRL", "source", 1_777_777_710L),
						new LedgerPostingCreatedLine(102L, 10L, 2002L, "CREDIT", 5000L, 5000L, "BRL", "destination", 1_777_777_710L)
				)
		);

		assertThrows(IllegalArgumentException.class, () -> service.project(event));
		assertEquals(1.0, counter("bank_flow_balance_projection_total", "result", "failed"));
	}

	@Test
	void failsWhenPostingIsNotBalanced() {
		LedgerPostingCreatedEvent event = new LedgerPostingCreatedEvent(
				10L,
				"transfer-1",
				"TRANSFER",
				"POSTED",
				"Transfer transfer-1",
				1_777_777_700L,
				1_777_777_710L,
				0L,
				"{}",
				List.of(
						new LedgerPostingCreatedLine(101L, 10L, 1001L, "DEBIT", 5000L, -5000L, "BRL", "source", 1_777_777_710L),
						new LedgerPostingCreatedLine(102L, 10L, 2002L, "CREDIT", 4000L, 4000L, "BRL", "destination", 1_777_777_710L)
				)
		);

		assertThrows(IllegalArgumentException.class, () -> service.project(event));
		assertEquals(1.0, counter("bank_flow_balance_projection_total", "result", "failed"));
	}

	private double counter(String name, String tagKey, String tagValue) {
		return meterRegistry.find(name)
				.tag(tagKey, tagValue)
				.counter()
				.count();
	}

	private LedgerPostingCreatedEvent validEvent() {
		return new LedgerPostingCreatedEvent(
				10L,
				"transfer-1",
				"TRANSFER",
				"POSTED",
				"Transfer transfer-1",
				1_777_777_700L,
				1_777_777_710L,
				0L,
				"{}",
				List.of(
						new LedgerPostingCreatedLine(101L, 10L, 1001L, "DEBIT", 5000L, -5000L, "BRL", "source", 1_777_777_710L),
						new LedgerPostingCreatedLine(102L, 10L, 2002L, "CREDIT", 5000L, 5000L, "BRL", "destination", 1_777_777_710L)
				)
		);
	}

	private static class FakeBalanceProjectionRepository implements BalanceProjectionRepository {
		private boolean firstProcessing = true;
		private int markProcessedCalls;
		private final List<LedgerPostingCreatedLine> savedLines = new ArrayList<>();
		private final List<LedgerPostingCreatedLine> appliedLines = new ArrayList<>();

		@Override
		public boolean markProcessedIfAbsent(LedgerPostingCreatedEvent event, long processedAt) {
			markProcessedCalls++;
			return firstProcessing;
		}

		@Override
		public void saveEntryLine(LedgerPostingCreatedEvent event, LedgerPostingCreatedLine line) {
			savedLines.add(line);
		}

		@Override
		public void applyPostedBalance(LedgerPostingCreatedLine line, long updatedAt) {
			appliedLines.add(line);
		}
	}
}

package br.com.bankflow.balance.services;

import br.com.bankflow.balance.domain.AccountBalance;
import br.com.bankflow.balance.domain.AccountStatementLine;
import br.com.bankflow.balance.repositories.BalanceQueryRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BalanceQueryServiceTests {
	private final FakeBalanceQueryRepository repository = new FakeBalanceQueryRepository();
	private final BalanceQueryService service = new BalanceQueryService(repository);

	@Test
	void returnsBalance() {
		AccountBalance balance = service.getBalance(1001L);

		assertEquals(1001L, balance.accountId());
		assertEquals("BRL", balance.currency());
		assertEquals(12_500L, balance.postedMinor());
		assertEquals(2_500L, balance.heldMinor());
		assertEquals(10_000L, balance.availableMinor());
	}

	@Test
	void failsWhenBalanceDoesNotExist() {
		assertThrows(BalanceNotFoundException.class, () -> service.getBalance(9999L));
	}

	@Test
	void returnsStatementWithDefaultLimit() {
		BalanceQueryService.AccountStatement statement = service.getStatement(1001L, null, null);

		assertEquals(50, statement.limit());
		assertEquals(2, statement.lines().size());
		assertNull(statement.nextCursor());
		assertEquals(50, repository.lastLimit);
		assertNull(repository.lastCursor);
	}

	@Test
	void capsStatementLimit() {
		String cursor = encodeCursor(123L, 456L);
		service.getStatement(1001L, 500, cursor);

		assertEquals(200, repository.lastLimit);
		assertEquals(123L, repository.lastCursor.occurredAt());
		assertEquals(456L, repository.lastCursor.lineId());
	}

	@Test
	void returnsNextCursorWhenPageIsFull() {
		BalanceQueryService.AccountStatement statement = service.getStatement(1001L, 2, null);

		assertEquals(encodeCursor(100L, 1L), statement.nextCursor());
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class, () -> service.getBalance(0L));
		assertThrows(IllegalArgumentException.class, () -> service.getStatement(1001L, 0, null));
		assertThrows(IllegalArgumentException.class, () -> service.getStatement(1001L, 10, "invalid"));
		assertThrows(IllegalArgumentException.class, () -> service.getStatement(1001L, 10, encodeCursor(0L, 1L)));
	}

	private String encodeCursor(long occurredAt, long lineId) {
		return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString("%d:%d".formatted(occurredAt, lineId).getBytes(StandardCharsets.UTF_8));
	}

	private static class FakeBalanceQueryRepository implements BalanceQueryRepository {
		private int lastLimit;
		private StatementCursor lastCursor;

		@Override
		public Optional<AccountBalance> findBalance(long accountId) {
			if (accountId == 1001L) {
				return Optional.of(new AccountBalance(1001L, "BRL", 12_500L, 2_500L, 1_777_777_777_000L));
			}
			return Optional.empty();
		}

		@Override
		public List<AccountStatementLine> findStatementLines(long accountId, int limit, StatementCursor cursor) {
			lastLimit = limit;
			lastCursor = cursor;
			return List.of(
					new AccountStatementLine(2L, 10L, 1001L, "transfer-1", "TRANSFER", "CREDIT", 7500L, 7500L, "BRL", "Transfer transfer-1", 200L, 210L),
					new AccountStatementLine(1L, 9L, 1001L, "transfer-0", "TRANSFER", "CREDIT", 5000L, 5000L, "BRL", "Transfer transfer-0", 100L, 110L)
			);
		}
	}
}

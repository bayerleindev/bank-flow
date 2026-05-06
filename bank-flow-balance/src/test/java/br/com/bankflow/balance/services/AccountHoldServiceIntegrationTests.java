package br.com.bankflow.balance.services;

import br.com.bankflow.balance.domain.AccountHold;
import br.com.bankflow.balance.domain.AccountHoldStatus;
import br.com.bankflow.balance.domain.CreateAccountHoldCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class AccountHoldServiceIntegrationTests {
	@Autowired
	private AccountHoldService accountHoldService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("DELETE FROM account_holds");
		jdbcTemplate.update("DELETE FROM account_balance_entries");
		jdbcTemplate.update("DELETE FROM processed_ledger_entries");
		jdbcTemplate.update("DELETE FROM account_balances");
		jdbcTemplate.update("""
				INSERT INTO account_balances (account_id, currency, posted_minor, held_minor, updated_at)
				VALUES (1001, 'BRL', 10000, 0, 1778000000000)
				""");
	}

	@Test
	void createsHoldAndReducesAvailableBalance() {
		AccountHold hold = accountHoldService.createHold(command("transfer-1", 1500L));

		assertEquals(AccountHoldStatus.HELD, hold.status());
		assertEquals(1500L, heldMinor());
		assertEquals(8500L, availableMinor());
	}

	@Test
	void returnsExistingHoldForSameTransferIdWithoutReservingAgain() {
		AccountHold first = accountHoldService.createHold(command("transfer-1", 1500L));
		AccountHold duplicate = accountHoldService.createHold(command("transfer-1", 1500L));

		assertEquals(first.holdId(), duplicate.holdId());
		assertEquals(1500L, heldMinor());
		assertEquals(1, countHolds());
	}

	@Test
	void failsWhenAvailableBalanceIsInsufficient() {
		assertThrows(InsufficientFundsException.class, () -> accountHoldService.createHold(command("transfer-1", 12000L)));

		assertEquals(0L, heldMinor());
		assertEquals(0, countHolds());
	}

	@Test
	void capturesHoldAndReleasesHeldBalance() {
		AccountHold hold = accountHoldService.createHold(command("transfer-1", 1500L));

		AccountHold captured = accountHoldService.captureHold(hold.holdId());

		assertEquals(AccountHoldStatus.CAPTURED, captured.status());
		assertEquals(0L, heldMinor());
		assertThrows(AccountHoldStateException.class, () -> accountHoldService.releaseHold(hold.holdId()));
	}

	@Test
	void releasesHoldAndReleasesHeldBalance() {
		AccountHold hold = accountHoldService.createHold(command("transfer-1", 1500L));

		AccountHold released = accountHoldService.releaseHold(hold.holdId());

		assertEquals(AccountHoldStatus.RELEASED, released.status());
		assertEquals(0L, heldMinor());
		assertThrows(AccountHoldStateException.class, () -> accountHoldService.captureHold(hold.holdId()));
	}

	private CreateAccountHoldCommand command(String transferId, long amountMinor) {
		return new CreateAccountHoldCommand(
				transferId,
				1001L,
				amountMinor,
				"BRL",
				"TRANSFER",
				System.currentTimeMillis() + 60_000L
		);
	}

	private long heldMinor() {
		Long held = jdbcTemplate.queryForObject(
				"SELECT held_minor FROM account_balances WHERE account_id = 1001",
				Long.class
		);
		return held == null ? 0L : held;
	}

	private long availableMinor() {
		Long available = jdbcTemplate.queryForObject(
				"SELECT posted_minor - held_minor FROM account_balances WHERE account_id = 1001",
				Long.class
		);
		return available == null ? 0L : available;
	}

	private int countHolds() {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_holds", Integer.class);
		return count == null ? 0 : count;
	}
}

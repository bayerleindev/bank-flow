package br.com.bankflow.balance.shared.repository;

import br.com.bankflow.balance.shared.domain.Balance;
import br.com.bankflow.balance.shared.domain.BalanceHoldStatus;
import br.com.bankflow.balance.shared.domain.HoldSettlementType;
import br.com.bankflow.balance.shared.domain.JournalEntrySide;
import br.com.bankflow.balance.shared.kafka.BalanceCaptureCommand;
import br.com.bankflow.balance.shared.kafka.BalanceHeldEvent;
import br.com.bankflow.balance.shared.kafka.BalanceHoldCommand;
import br.com.bankflow.balance.shared.kafka.BalanceReleaseCommand;
import br.com.bankflow.balance.shared.kafka.LedgerJournalEntryCreatedEvent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BalanceRepository {

    private static final RowMapper<Balance> BALANCE_ROW_MAPPER =
            (resultSet, rowNumber) ->
                    new Balance(
                            resultSet.getObject("account_id", UUID.class),
                            resultSet.getString("currency"),
                            resultSet.getLong("total_amount_minor"),
                            resultSet.getLong("held_amount_minor"),
                            resultSet.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public BalanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void checkDatabaseConnection() {
        jdbcTemplate.queryForObject("select 1", Integer.class);
    }

    public List<Balance> findByAccountId(UUID accountId) {
        return jdbcTemplate.query(
                """
				select account_id,
					currency,
					total_amount_minor,
					held_amount_minor,
					updated_at
				from balance.account_balances
				where account_id = ?
				order by currency
				""",
                BALANCE_ROW_MAPPER,
                accountId);
    }

    @Transactional
    public boolean applyEntry(
            UUID transferId, LedgerJournalEntryCreatedEvent entry, Instant postedAt) {
        if (entryAlreadyProcessed(entry)) {
            return false;
        }

        upsertBalance(entry, postedAt);
        insertProcessedEntry(entry, postedAt);
        return true;
    }

    @Transactional
    public BalanceHeldEvent hold(BalanceHoldCommand command, Instant heldAt) {
        BalanceHeldEvent existingEvent = findHoldEvent(command, heldAt);
        if (existingEvent != null) {
            return existingEvent;
        }

        Balance balance = findBalance(command.debitAccountId(), command.currency());
        if (balance == null) {
            return reject(command, "account_balance_not_found", heldAt);
        }

        if (balance.availableAmountMinor() < command.amountMinor()) {
            return reject(command, "insufficient_balance", heldAt);
        }

        jdbcTemplate.update(
                """
				update balance.account_balances
				set held_amount_minor = held_amount_minor + ?,
					updated_at = ?
				where account_id = ?
					and currency = ?
				""",
                command.amountMinor(),
                Timestamp.from(heldAt),
                command.debitAccountId(),
                command.currency());

        insertHold(command, BalanceHoldStatus.HELD, null, heldAt);
        return toEvent(command, BalanceHoldStatus.HELD, null, heldAt);
    }

    @Transactional
    public boolean capture(BalanceCaptureCommand command, Instant processedAt) {
        if (settlementAlreadyProcessed(command.transferId(), HoldSettlementType.CAPTURE)) {
            return false;
        }

        if (!heldBalanceExists(command.transferId(), command.accountId(), command.amountMinor())) {
            return false;
        }

        jdbcTemplate.update(
                """
				update balance.account_balances
				set held_amount_minor = held_amount_minor - ?,
					updated_at = ?
				where account_id = ?
					and currency = ?
				""",
                command.amountMinor(),
                Timestamp.from(processedAt),
                command.accountId(),
                command.currency());

        insertSettlement(command.transferId(), HoldSettlementType.CAPTURE, processedAt);
        return true;
    }

    @Transactional
    public boolean release(BalanceReleaseCommand command, Instant processedAt) {
        if (settlementAlreadyProcessed(command.transferId(), HoldSettlementType.RELEASE)) {
            return false;
        }

        if (!heldBalanceExists(command.transferId(), command.accountId(), command.amountMinor())) {
            return false;
        }

        jdbcTemplate.update(
                """
				update balance.account_balances
				set held_amount_minor = held_amount_minor - ?,
					updated_at = ?
				where account_id = ?
					and currency = ?
				""",
                command.amountMinor(),
                Timestamp.from(processedAt),
                command.accountId(),
                command.currency());

        insertSettlement(command.transferId(), HoldSettlementType.RELEASE, processedAt);
        return true;
    }

    private boolean settlementAlreadyProcessed(UUID transferId, HoldSettlementType type) {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        """
						select exists(
							select 1
							from balance.hold_settlements
							where transfer_id = ?
								and type = ?
						)
						""",
                        Boolean.class,
                        transferId,
                        type.name());
        return Boolean.TRUE.equals(exists);
    }

    private boolean heldBalanceExists(UUID transferId, UUID accountId, long amountMinor) {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        """
						select exists(
							select 1
							from balance.balance_holds
							where transfer_id = ?
								and account_id = ?
								and amount_minor = ?
								and status = ?
						)
						""",
                        Boolean.class,
                        transferId,
                        accountId,
                        amountMinor,
                        BalanceHoldStatus.HELD.name());
        return Boolean.TRUE.equals(exists);
    }

    private void insertSettlement(UUID transferId, HoldSettlementType type, Instant processedAt) {
        jdbcTemplate.update(
                """
				insert into balance.hold_settlements (
					transfer_id,
					type,
					status,
					processed_at
				) values (?, ?, ?, ?)
				""",
                transferId,
                type.name(),
                "PROCESSED",
                Timestamp.from(processedAt));
    }

    private BalanceHeldEvent findHoldEvent(BalanceHoldCommand command, Instant fallbackHeldAt) {
        List<BalanceHeldEvent> events =
                jdbcTemplate.query(
                        """
						select transfer_id,
							account_id,
							amount_minor,
							currency,
							status,
							reason,
							created_at
						from balance.balance_holds
						where transfer_id = ?
						""",
                        (resultSet, rowNumber) ->
                                new BalanceHeldEvent(
                                        resultSet.getObject("transfer_id", UUID.class),
                                        BalanceHoldStatus.valueOf(resultSet.getString("status")),
                                        resultSet.getString("reason"),
                                        resultSet.getObject("account_id", UUID.class),
                                        resultSet.getLong("amount_minor"),
                                        resultSet.getString("currency"),
                                        resultSet.getTimestamp("created_at").toInstant()),
                        command.transferId());

        if (events.isEmpty()) {
            return null;
        }
        return events.get(0);
    }

    private Balance findBalance(UUID accountId, String currency) {
        List<Balance> balances =
                jdbcTemplate.query(
                        """
						select account_id,
							currency,
							total_amount_minor,
							held_amount_minor,
							updated_at
						from balance.account_balances
						where account_id = ?
							and currency = ?
						""",
                        BALANCE_ROW_MAPPER,
                        accountId,
                        currency);

        if (balances.isEmpty()) {
            return null;
        }
        return balances.get(0);
    }

    private BalanceHeldEvent reject(BalanceHoldCommand command, String reason, Instant heldAt) {
        insertHold(command, BalanceHoldStatus.REJECTED, reason, heldAt);
        return toEvent(command, BalanceHoldStatus.REJECTED, reason, heldAt);
    }

    private void insertHold(
            BalanceHoldCommand command,
            BalanceHoldStatus status,
            String reason,
            Instant createdAt) {
        jdbcTemplate.update(
                """
				insert into balance.balance_holds (
					transfer_id,
					account_id,
					amount_minor,
					currency,
					status,
					reason,
					created_at
				) values (?, ?, ?, ?, ?, ?, ?)
				""",
                command.transferId(),
                command.debitAccountId(),
                command.amountMinor(),
                command.currency(),
                status.name(),
                reason,
                Timestamp.from(createdAt));
    }

    private BalanceHeldEvent toEvent(
            BalanceHoldCommand command, BalanceHoldStatus status, String reason, Instant heldAt) {
        return new BalanceHeldEvent(
                command.transferId(),
                status,
                reason,
                command.debitAccountId(),
                command.amountMinor(),
                command.currency(),
                heldAt);
    }

    private boolean entryAlreadyProcessed(LedgerJournalEntryCreatedEvent entry) {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        """
						select exists(
							select 1
							from balance.processed_journal_entries
							where movement_id = ?
								and account_id = ?
								and side = ?
						)
						""",
                        Boolean.class,
                        entry.movementId(),
                        entry.accountId(),
                        entry.side().name());
        return Boolean.TRUE.equals(exists);
    }

    private void upsertBalance(LedgerJournalEntryCreatedEvent entry, Instant updatedAt) {
        jdbcTemplate.update(
                """
				insert into balance.account_balances (
					account_id,
					currency,
					total_amount_minor,
					held_amount_minor,
					updated_at
				) values (?, ?, ?, 0, ?)
				on conflict (account_id, currency)
				do update set
					total_amount_minor =
						balance.account_balances.total_amount_minor + excluded.total_amount_minor,
					updated_at = excluded.updated_at
				""",
                entry.accountId(),
                entry.currency(),
                signedAmount(entry),
                Timestamp.from(updatedAt));
    }

    private long signedAmount(LedgerJournalEntryCreatedEvent entry) {
        if (entry.side() == JournalEntrySide.DEBIT) {
            return -entry.amountMinor();
        }
        return entry.amountMinor();
    }

    private void insertProcessedEntry(LedgerJournalEntryCreatedEvent entry, Instant processedAt) {
        jdbcTemplate.update(
                """
				insert into balance.processed_journal_entries (
					movement_id,
					account_id,
					side,
					processed_at
				) values (?, ?, ?, ?)
				""",
                entry.movementId(),
                entry.accountId(),
                entry.side().name(),
                Timestamp.from(processedAt));
    }
}

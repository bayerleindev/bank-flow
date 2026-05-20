package br.com.bankflow.transfers.shared.repository;

import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TransferRepository {

    private static final RowMapper<Transfer> TRANSFER_ROW_MAPPER =
            (resultSet, rowNumber) ->
                    new Transfer(
                            resultSet.getObject("id", UUID.class),
                            new TransferParty(
                                    resultSet.getString("debit_bank"),
                                    resultSet.getString("debit_account"),
                                    resultSet.getString("debit_branch")),
                            new TransferParty(
                                    resultSet.getString("credit_bank"),
                                    resultSet.getString("credit_account"),
                                    resultSet.getString("credit_branch")),
                            resultSet.getString("idempotency_key"),
                            resultSet.getLong("amount_minor"),
                            resultSet.getString("description"),
                            resultSet.getString("currency"),
                            TransferType.valueOf(resultSet.getString("type")),
                            resultSet.getString("status"),
                            resultSet.getString("rejection_reason"),
                            resultSet.getObject("debit_account_id", UUID.class),
                            resultSet.getObject("credit_account_id", UUID.class),
                            resultSet.getTimestamp("created_at").toInstant(),
                            resultSet.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public TransferRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void checkDatabaseConnection() {
        jdbcTemplate.queryForObject("select 1", Integer.class);
    }

    public Transfer save(Transfer transfer) {
        jdbcTemplate.update(
                """
				insert into transfers.transfers (
					id,
					debit_bank,
					debit_account,
					debit_branch,
					credit_bank,
					credit_account,
					credit_branch,
					idempotency_key,
					amount_minor,
					description,
						currency,
						type,
						status,
						rejection_reason,
						debit_account_id,
						credit_account_id,
						created_at,
						updated_at
					) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
                transfer.id(),
                transfer.debitParty().bank(),
                transfer.debitParty().account(),
                transfer.debitParty().branch(),
                transfer.creditParty().bank(),
                transfer.creditParty().account(),
                transfer.creditParty().branch(),
                transfer.idempotencyKey(),
                transfer.amountMinor(),
                transfer.description(),
                transfer.currency(),
                transfer.type().name(),
                transfer.status(),
                transfer.rejectionReason(),
                transfer.debitAccountId(),
                transfer.creditAccountId(),
                Timestamp.from(transfer.createdAt()),
                Timestamp.from(transfer.updatedAt()));
        return transfer;
    }

    public Optional<Transfer> findById(UUID id) {
        try {
            Transfer transfer =
                    jdbcTemplate.queryForObject(
                            """
							select
								id,
								debit_bank,
								debit_account,
								debit_branch,
								credit_bank,
								credit_account,
								credit_branch,
								idempotency_key,
								amount_minor,
								description,
								currency,
								type,
								status,
								rejection_reason,
								debit_account_id,
								credit_account_id,
								created_at,
								updated_at
							from transfers.transfers
							where id = ?
							""",
                            TRANSFER_ROW_MAPPER,
                            id);
            return Optional.ofNullable(transfer);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public boolean existsById(UUID id) {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        "select exists(select 1 from transfers.transfers where id = ?)",
                        Boolean.class,
                        id);
        return Boolean.TRUE.equals(exists);
    }

    public boolean updateStatus(
            UUID id, String currentStatus, String nextStatus, Instant updatedAt) {
        int updatedRows =
                jdbcTemplate.update(
                        """
						update transfers.transfers
						set status = ?,
							updated_at = ?
						where id = ?
							and status = ?
						""",
                        nextStatus,
                        Timestamp.from(updatedAt),
                        id,
                        currentStatus);
        return updatedRows == 1;
    }

    public boolean updateValidatedAccountIds(
            UUID id, UUID debitAccountId, UUID creditAccountId, Instant updatedAt) {
        int updatedRows =
                jdbcTemplate.update(
                        """
						update transfers.transfers
						set debit_account_id = ?,
							credit_account_id = ?,
							updated_at = ?
						where id = ?
						""",
                        debitAccountId,
                        creditAccountId,
                        Timestamp.from(updatedAt),
                        id);
        return updatedRows == 1;
    }

    public boolean reject(
            UUID id, String currentStatus, String rejectionReason, Instant updatedAt) {
        int updatedRows =
                jdbcTemplate.update(
                        """
						update transfers.transfers
						set status = ?,
							rejection_reason = ?,
							updated_at = ?
						where id = ?
							and status = ?
						""",
                        "REJECTED",
                        rejectionReason,
                        Timestamp.from(updatedAt),
                        id,
                        currentStatus);
        return updatedRows == 1;
    }

    public boolean complete(UUID id, String currentStatus, Instant updatedAt) {
        int updatedRows =
                jdbcTemplate.update(
                        """
						update transfers.transfers
						set status = ?,
							rejection_reason = null,
							updated_at = ?
						where id = ?
							and status = ?
						""",
                        "COMPLETED",
                        Timestamp.from(updatedAt),
                        id,
                        currentStatus);
        return updatedRows == 1;
    }
}

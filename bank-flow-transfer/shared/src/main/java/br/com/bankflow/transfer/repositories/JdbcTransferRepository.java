package br.com.bankflow.transfer.repositories;

import br.com.bankflow.transfer.domain.CreateTransferCommand;
import br.com.bankflow.transfer.domain.Transfer;
import br.com.bankflow.transfer.domain.TransferStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

@Repository
public class JdbcTransferRepository implements TransferRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcTransferRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<Transfer> findByIdempotencyKey(String idempotencyKey) {
		List<Transfer> transfers = jdbcTemplate.query("""
				SELECT transfer_id, idempotency_key, source_digital_account_id, source_account,
				       destination_digital_account_id, destination_account, amount_minor,
				       currency, description, hold_id, psp_payment_id, status, failure_reason,
				       created_at, updated_at
				FROM transfers
				WHERE idempotency_key = ?
				""", this::mapTransfer, idempotencyKey);
		return transfers.stream().findFirst();
	}

	@Override
	public Optional<Transfer> findByTransferId(UUID transferId) {
		List<Transfer> transfers = jdbcTemplate.query("""
				SELECT transfer_id, idempotency_key, source_digital_account_id, source_account,
				       destination_digital_account_id, destination_account, amount_minor,
				       currency, description, hold_id, psp_payment_id, status, failure_reason,
				       created_at, updated_at, traceparent
				FROM transfers
				WHERE transfer_id = ?
				""", this::mapTransfer, transferId);
		return transfers.stream().findFirst();
	}

	@Override
	public Optional<Transfer> findByPspPaymentId(String pspPaymentId) {
		List<Transfer> transfers = jdbcTemplate.query("""
				SELECT transfer_id, idempotency_key, source_digital_account_id, source_account,
				       destination_digital_account_id, destination_account, amount_minor,
				       currency, description, hold_id, psp_payment_id, status, failure_reason,
				       created_at, updated_at, traceparent
				FROM transfers
				WHERE psp_payment_id = ?
				""", this::mapTransfer, pspPaymentId);
		return transfers.stream().findFirst();
	}

	@Override
	public long countByStatus(TransferStatus status) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM transfers
				WHERE status = ?
				""", Long.class, status.name());
		return count == null ? 0L : count;
	}

	@Override
	public OptionalLong oldestUpdatedAtByStatus(TransferStatus status) {
		Long oldestUpdatedAt = jdbcTemplate.queryForObject("""
				SELECT MIN(updated_at)
				FROM transfers
				WHERE status = ?
				""", Long.class, status.name());
		return oldestUpdatedAt == null ? OptionalLong.empty() : OptionalLong.of(oldestUpdatedAt);
	}

	@Override
	public Transfer create(
            UUID transferId,
            CreateTransferCommand command,
            String sourceAccount,
            String destinationAccount,
            long now) {
		jdbcTemplate.update("""
				INSERT INTO transfers (
					transfer_id, idempotency_key, source_digital_account_id, source_account,
					source_account_id, destination_digital_account_id, destination_account,
					destination_account_id, amount_minor,
					currency, description, status, created_at, updated_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				transferId,
				command.idempotencyKey(),
				command.sourceDigitalAccountId(),
				sourceAccount,
				null,
				command.destinationDigitalAccountId(),
				destinationAccount,
				null,
				command.amountMinor(),
				command.currency(),
				command.description(),
				TransferStatus.RECEIVED.name(),
				now,
				now
		);
		return findByTransferId(transferId).orElseThrow();
	}

	@Override
	public Transfer updateHold(UUID transferId, String holdId, TransferStatus status, long now) {
		jdbcTemplate.update("""
				UPDATE transfers
				SET hold_id = ?,
				    status = ?,
				    updated_at = ?
				WHERE transfer_id = ?
				""", holdId, status.name(), now, transferId);
		return findByTransferId(transferId).orElseThrow();
	}

	@Override
	public Transfer updatePspPayment(UUID transferId, String pspPaymentId, TransferStatus status, long now) {
		jdbcTemplate.update("""
				UPDATE transfers
				SET psp_payment_id = ?,
				    status = ?,
				    updated_at = ?
				WHERE transfer_id = ?
				""", pspPaymentId, status.name(), now, transferId);
		return findByTransferId(transferId).orElseThrow();
	}

	@Override
	public Transfer updateStatus(UUID transferId, TransferStatus status, String failureReason, long now) {
		jdbcTemplate.update("""
				UPDATE transfers
				SET status = ?,
				    failure_reason = ?,
				    updated_at = ?
				WHERE transfer_id = ?
				""", status.name(), failureReason, now, transferId);
		return findByTransferId(transferId).orElseThrow();
	}

	private Transfer mapTransfer(ResultSet rs, int rowNum) throws SQLException {
		return new Transfer(
				(UUID) rs.getObject("transfer_id"),
				rs.getString("idempotency_key"),
				(UUID) rs.getObject("source_digital_account_id"),
				rs.getString("source_account"),
				(UUID) rs.getObject("destination_digital_account_id"),
				rs.getString("destination_account"),
				rs.getLong("amount_minor"),
				rs.getString("currency"),
				rs.getString("description"),
				rs.getString("hold_id"),
				rs.getString("psp_payment_id"),
				TransferStatus.valueOf(rs.getString("status")),
				rs.getString("failure_reason"),
				rs.getLong("created_at"),
				rs.getLong("updated_at")
		);
	}
}

package br.com.bankflow.ledger.repositories;

import br.com.bankflow.ledger.configs.ImmuDbClientFactory;
import br.com.bankflow.ledger.domain.LedgerEntry;
import br.com.bankflow.ledger.domain.LedgerEntryLine;
import br.com.bankflow.ledger.domain.LedgerPosting;
import io.codenotary.immudb4j.ImmuClient;
import io.codenotary.immudb4j.sql.SQLException;
import io.codenotary.immudb4j.sql.SQLQueryResult;
import io.codenotary.immudb4j.sql.SQLValue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "bank-flow.immudb", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ImmuDbLedgerPostingRepository implements LedgerPostingRepository {
	private static final String FIND_ENTRY_ID_BY_EXTERNAL_ID = """
			SELECT entry_id
			FROM ledger_entries
			WHERE external_id = $1
			LIMIT 1
			""";
	private static final String FIND_ENTRY_BY_EXTERNAL_ID = """
			SELECT entry_id, external_id, entry_type, status, description, occurred_at, created_at, reversal_of_entry_id, metadata
			FROM ledger_entries
			WHERE external_id = $1
			LIMIT 1
			""";
	private static final String FIND_LINES_BY_ENTRY_ID = """
			SELECT line_id, entry_id, account_id, direction, amount_minor, signed_amount_minor, currency, line_memo, created_at
			FROM ledger_entry_lines
			WHERE entry_id = $1
			""";
	private static final String FIND_REVERSAL_BY_ENTRY_ID = """
			SELECT entry_id
			FROM ledger_entries
			WHERE reversal_of_entry_id = $1
			AND entry_type = 'REVERSAL'
			LIMIT 1
			""";
	private static final String INSERT_LEDGER_ENTRY = """
			INSERT INTO ledger_entries (
				entry_id,
				external_id,
				entry_type,
				status,
				description,
				occurred_at,
				created_at,
				reversal_of_entry_id,
				metadata
			) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
			""";
	private static final String INSERT_LEDGER_ENTRY_LINE = """
			INSERT INTO ledger_entry_lines (
				line_id,
				entry_id,
				account_id,
				direction,
				amount_minor,
				signed_amount_minor,
				currency,
				line_memo,
				created_at
			) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
			""";

	private final ImmuDbClientFactory clientFactory;

	public ImmuDbLedgerPostingRepository(ImmuDbClientFactory clientFactory) {
		this.clientFactory = clientFactory;
	}

	@Override
	public boolean saveIfNotExists(LedgerPosting posting) {
		ImmuClient immuClient = clientFactory.openClient();
		LedgerEntry entry = posting.entry();
		boolean transactionStarted = false;
		try {
			immuClient.beginTransaction();
			transactionStarted = true;
			if (entryAlreadyExists(immuClient, entry.externalId())) {
				immuClient.commitTransaction();
				return false;
			}
			insertEntry(immuClient, entry);
			for (LedgerEntryLine line : posting.lines()) {
				insertLine(immuClient, line);
			}
			immuClient.commitTransaction();
			return true;
		} catch (SQLException exception) {
			if (transactionStarted) {
				rollbackTransaction(immuClient);
			}
			if (isUniqueViolation(exception) && entryAlreadyExistsWithNewClient(entry.externalId())) {
				return false;
			}
			throw new ImmuDbPersistenceException("failed to save ledger posting on immudb", exception);
		} catch (RuntimeException exception) {
			if (transactionStarted) {
				rollbackTransaction(immuClient);
			}
			if (isUniqueViolation(exception) && entryAlreadyExistsWithNewClient(entry.externalId())) {
				return false;
			}
			throw new ImmuDbPersistenceException("failed to save ledger posting on immudb", exception);
		} finally {
			clientFactory.closeClient(immuClient);
		}
	}

	@Override
	public Optional<LedgerPosting> findByExternalId(String externalId) {
		ImmuClient immuClient = clientFactory.openClient();
		boolean transactionStarted = false;
		try {
			immuClient.beginTransaction();
			transactionStarted = true;
			Optional<LedgerEntry> entry = findEntryByExternalId(immuClient, externalId);
			if (entry.isEmpty()) {
				immuClient.commitTransaction();
				return Optional.empty();
			}
			List<LedgerEntryLine> lines = findLinesByEntryId(immuClient, entry.get().entryId());
			immuClient.commitTransaction();
			return Optional.of(LedgerPosting.of(entry.get(), lines));
		} catch (SQLException | RuntimeException exception) {
			if (transactionStarted) {
				rollbackTransaction(immuClient);
			}
			throw new ImmuDbPersistenceException("failed to find ledger posting on immudb", exception);
		} finally {
			clientFactory.closeClient(immuClient);
		}
	}

	@Override
	public boolean reversalExistsFor(long entryId) {
		ImmuClient immuClient = clientFactory.openClient();
		boolean transactionStarted = false;
		try {
			immuClient.beginTransaction();
			transactionStarted = true;
			SQLQueryResult result = immuClient.sqlQuery(
					FIND_REVERSAL_BY_ENTRY_ID,
					new SQLValue(entryId)
			);
			try {
				boolean exists = result.next();
				immuClient.commitTransaction();
				return exists;
			} finally {
				result.close();
			}
		} catch (SQLException | RuntimeException exception) {
			if (transactionStarted) {
				rollbackTransaction(immuClient);
			}
			throw new ImmuDbPersistenceException("failed to find ledger reversal on immudb", exception);
		} finally {
			clientFactory.closeClient(immuClient);
		}
	}

	boolean entryAlreadyExists(ImmuClient immuClient, String externalId) throws SQLException {
		return queryEntryAlreadyExists(immuClient, externalId);
	}

	private boolean queryEntryAlreadyExists(ImmuClient immuClient, String externalId) throws SQLException {
		SQLQueryResult result = immuClient.sqlQuery(
				FIND_ENTRY_ID_BY_EXTERNAL_ID,
				new SQLValue(externalId)
		);
		try {
			return result.next();
		} finally {
			result.close();
		}
	}

	private Optional<LedgerEntry> findEntryByExternalId(ImmuClient immuClient, String externalId) throws SQLException {
		SQLQueryResult result = immuClient.sqlQuery(
				FIND_ENTRY_BY_EXTERNAL_ID,
				new SQLValue(externalId)
		);
		try {
			if (!result.next()) {
				return Optional.empty();
			}
			return Optional.of(new LedgerEntry(
					result.getLong(0),
					result.getString(1),
					result.getString(2),
					result.getString(3),
					result.getString(4),
					result.getLong(5),
					result.getLong(6),
					result.getLong(7),
					result.getString(8)
			));
		} finally {
			result.close();
		}
	}

	private List<LedgerEntryLine> findLinesByEntryId(ImmuClient immuClient, long entryId) throws SQLException {
		SQLQueryResult result = immuClient.sqlQuery(
				FIND_LINES_BY_ENTRY_ID,
				new SQLValue(entryId)
		);
		try {
			List<LedgerEntryLine> lines = new ArrayList<>();
			while (result.next()) {
				lines.add(new LedgerEntryLine(
						result.getLong(0),
						result.getLong(1),
						result.getLong(2),
						result.getString(3),
						result.getLong(4),
						result.getLong(5),
						result.getString(6),
						result.getString(7),
						result.getLong(8)
				));
			}
			return lines;
		} finally {
			result.close();
		}
	}

	private boolean entryAlreadyExistsWithNewClient(String externalId) {
		ImmuClient immuClient = clientFactory.openClient();
		boolean transactionStarted = false;
		try {
			immuClient.beginTransaction();
			transactionStarted = true;
			boolean exists = queryEntryAlreadyExists(immuClient, externalId);
			immuClient.commitTransaction();
			return exists;
		} catch (SQLException | RuntimeException exception) {
			if (transactionStarted) {
				rollbackTransaction(immuClient);
			}
			return false;
		} finally {
			clientFactory.closeClient(immuClient);
		}
	}

	private boolean isUniqueViolation(Throwable exception) {
		String normalizedMessage = exceptionChainMessage(exception).toLowerCase();
		return normalizedMessage.contains("unique")
				|| normalizedMessage.contains("already exists");
	}

	private String exceptionChainMessage(Throwable exception) {
		StringBuilder message = new StringBuilder();
		Throwable current = exception;
		while (current != null) {
			if (current.getMessage() != null) {
				message.append(current.getMessage()).append(' ');
			}
			current = current.getCause();
		}
		return message.toString();
	}

	private void insertEntry(ImmuClient immuClient, LedgerEntry entry) throws SQLException {
		immuClient.sqlExec(
				INSERT_LEDGER_ENTRY,
				new SQLValue(entry.entryId()),
				new SQLValue(entry.externalId()),
				new SQLValue(entry.entryType()),
				new SQLValue(entry.status()),
				new SQLValue(entry.description()),
				new SQLValue(entry.occurredAt()),
				new SQLValue(entry.createdAt()),
				new SQLValue(entry.reversalOfEntryId()),
				new SQLValue(entry.metadata())
		);
	}

	private void insertLine(ImmuClient immuClient, LedgerEntryLine line) throws SQLException {
		immuClient.sqlExec(
				INSERT_LEDGER_ENTRY_LINE,
				new SQLValue(line.lineId()),
				new SQLValue(line.entryId()),
				new SQLValue(line.accountId()),
				new SQLValue(line.direction()),
				new SQLValue(line.amountMinor()),
				new SQLValue(line.signedAmountMinor()),
				new SQLValue(line.currency()),
				new SQLValue(line.lineMemo()),
				new SQLValue(line.createdAt())
		);
	}

	private void rollbackTransaction(ImmuClient immuClient) {
		try {
			immuClient.rollbackTransaction();
		} catch (SQLException | RuntimeException ignored) {
		}
	}
}

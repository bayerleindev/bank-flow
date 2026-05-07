package br.com.bankflow.ledger.repositories;

import br.com.bankflow.ledger.configs.ImmuDbClientFactory;
import br.com.bankflow.ledger.domain.LedgerAccount;
import io.codenotary.immudb4j.ImmuClient;
import io.codenotary.immudb4j.sql.SQLException;
import io.codenotary.immudb4j.sql.SQLQueryResult;
import io.codenotary.immudb4j.sql.SQLValue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.OptionalLong;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "bank-flow.immudb", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ImmuDbLedgerAccountRepository implements LedgerAccountRepository {
	private static final String INSERT_LEDGER_ACCOUNT = """
			INSERT INTO ledger_accounts (
				account_id,
				account_code,
				account_name,
				account_type,
				normal_balance,
				currency,
				owner_type,
				owner_id,
				active,
				created_at,
				owner_id_old
			) VALUES ($1, $2, $3, $4, $5, $6, $7, $8::UUID, $9, $10, 1)
			""";
	private static final String FIND_ACCOUNT_ID_BY_ACCOUNT_CODE = """
			SELECT account_id
			FROM ledger_accounts
			WHERE account_code = $1
			LIMIT 1
			""";
	private static final String FIND_ACCOUNT_ID_BY_OWNER_ID = """
			SELECT account_id
			FROM ledger_accounts
			WHERE owner_id = $1::UUID
			AND active = true
			LIMIT 1
			""";

	private final ImmuDbClientFactory clientFactory;

	public ImmuDbLedgerAccountRepository(ImmuDbClientFactory clientFactory) {
		this.clientFactory = clientFactory;
	}

	@Override
	public boolean saveIfNotExists(LedgerAccount account) {
		ImmuClient immuClient = clientFactory.openClient();
		boolean transactionStarted = false;
		try {
			immuClient.beginTransaction();
			transactionStarted = true;
			if (accountAlreadyExists(immuClient, account.accountCode())) {
				immuClient.commitTransaction();
				return false;
			}
			immuClient.sqlExec(
					INSERT_LEDGER_ACCOUNT,
					new SQLValue(account.accountId()),
					new SQLValue(account.accountCode()),
					new SQLValue(account.accountName()),
					new SQLValue(account.accountType()),
					new SQLValue(account.normalBalance()),
					new SQLValue(account.currency()),
					new SQLValue(account.ownerType()),
					new SQLValue(account.digitalAccountId().toString()),
					new SQLValue(account.active()),
					new SQLValue(account.createdAt())
			);
			immuClient.commitTransaction();
			return true;
		} catch (SQLException | RuntimeException exception) {
			if (transactionStarted) {
				rollbackTransaction(immuClient);
			}
			if (isUniqueViolation(exception) && accountAlreadyExistsWithNewClient(account.accountCode())) {
				return false;
			}
			throw new ImmuDbPersistenceException("failed to save ledger account on immudb", exception);
		} finally {
			clientFactory.closeClient(immuClient);
		}
	}

	@Override
	public OptionalLong findAccountIdByDigitalAccountId(UUID digitalAccountId) {
		ImmuClient immuClient = clientFactory.openClient();
		boolean transactionStarted = false;
		try {
			immuClient.beginTransaction();
			transactionStarted = true;
			SQLQueryResult result = immuClient.sqlQuery(
					FIND_ACCOUNT_ID_BY_OWNER_ID,
					new SQLValue(digitalAccountId.toString())
			);
			try {
				if (!result.next()) {
					immuClient.commitTransaction();
					return OptionalLong.empty();
				}
				long accountId = result.getLong(0);
				immuClient.commitTransaction();
				return OptionalLong.of(accountId);
			} finally {
				result.close();
			}
		} catch (SQLException | RuntimeException exception) {
			if (transactionStarted) {
				rollbackTransaction(immuClient);
			}
			throw new ImmuDbPersistenceException("failed to find ledger account on immudb", exception);
		} finally {
			clientFactory.closeClient(immuClient);
		}
	}

	private boolean accountAlreadyExists(ImmuClient immuClient, String accountCode) throws SQLException {
		SQLQueryResult result = immuClient.sqlQuery(
				FIND_ACCOUNT_ID_BY_ACCOUNT_CODE,
				new SQLValue(accountCode)
		);
		try {
			return result.next();
		} finally {
			result.close();
		}
	}

	private boolean accountAlreadyExistsWithNewClient(String accountCode) {
		ImmuClient immuClient = clientFactory.openClient();
		boolean transactionStarted = false;
		try {
			immuClient.beginTransaction();
			transactionStarted = true;
			boolean exists = accountAlreadyExists(immuClient, accountCode);
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

	private void rollbackTransaction(ImmuClient immuClient) {
		try {
			immuClient.rollbackTransaction();
		} catch (SQLException | RuntimeException ignored) {
		}
	}
}

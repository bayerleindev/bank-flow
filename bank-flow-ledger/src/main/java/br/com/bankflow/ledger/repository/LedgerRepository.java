package br.com.bankflow.ledger.repository;

import br.com.bankflow.ledger.domain.Journal;
import br.com.bankflow.ledger.domain.JournalEntry;
import br.com.bankflow.ledger.domain.LedgerAccount;
import io.codenotary.immudb4j.ImmuClient;
import io.codenotary.immudb4j.sql.SQLException;
import io.codenotary.immudb4j.sql.SQLQueryResult;
import io.codenotary.immudb4j.sql.SQLValue;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("PMD.TooManyMethods")
public class LedgerRepository {

    private static final String INITIAL_MIGRATION_VERSION = "1";
    private static final String ACCOUNT_TYPE_MIGRATION_VERSION = "2";
    private static final String CURRENT_MIGRATION_VERSION = ACCOUNT_TYPE_MIGRATION_VERSION;

    private final ImmuClient immuClient;

    public LedgerRepository(ImmuClient immuClient) {
        this.immuClient = immuClient;
    }

    public void bootstrapMigrations() {
        inTransaction(this::createTables);
        inTransaction(this::applyAccountTypeMigration);
        inTransaction(
                () -> {
                    if (migrationExists(CURRENT_MIGRATION_VERSION)) {
                        return;
                    }

                    exec(
                            """
                            INSERT INTO ledger_migrations(version, description)
                            VALUES (@version, @description)
                            """,
                            Map.of(
                                    "version", new SQLValue(CURRENT_MIGRATION_VERSION),
                                    "description", new SQLValue("ledger account type migration")));
                });
    }

    public boolean existsAccount(UUID accountId) {
        return inTransaction(
                () ->
                        exists(
                                "SELECT account_id FROM ledger_accounts WHERE account_id = @accountId",
                                Map.of("accountId", new SQLValue(accountId.toString()))));
    }

    public void saveAccount(LedgerAccount account) {
        inTransaction(
                () -> {
                    if (exists(
                            "SELECT account_id FROM ledger_accounts WHERE account_id = @accountId",
                            Map.of("accountId", new SQLValue(account.accountId().toString())))) {
                        updateAccountType(account);
                        return;
                    }

                    exec(
                            """
                            INSERT INTO ledger_accounts(
                                account_id,
                                document_number,
                                branch_number,
                                account_number,
                                account_digit,
                                currency,
                                type,
                                created_at
                            )
                            VALUES (
                                @accountId,
                                @documentNumber,
                                @branchNumber,
                                @accountNumber,
                                @accountDigit,
                                @currency,
                                @type,
                                @createdAt
                            )
                            """,
                            Map.of(
                                    "accountId", new SQLValue(account.accountId().toString()),
                                    "documentNumber", new SQLValue(account.documentNumber()),
                                    "branchNumber", new SQLValue(account.branchNumber()),
                                    "accountNumber", new SQLValue(account.accountNumber()),
                                    "accountDigit", new SQLValue(account.accountDigit()),
                                    "currency", new SQLValue(account.currency()),
                                    "type", new SQLValue(account.type().name()),
                                    "createdAt", new SQLValue(account.createdAt().toString())));
                });
    }

    private void updateAccountType(LedgerAccount account) {
        exec(
                """
                UPDATE ledger_accounts
                SET type = @type
                WHERE account_id = @accountId
                """,
                Map.of(
                        "accountId", new SQLValue(account.accountId().toString()),
                        "type", new SQLValue(account.type().name())));
    }

    public boolean movementAlreadyPosted(UUID movementId) {
        return inTransaction(
                () ->
                        exists(
                                """
                                SELECT movement_id
                                FROM ledger_movement_idempotency
                                WHERE movement_id = @movementId
                                """,
                                Map.of("movementId", new SQLValue(movementId.toString()))));
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void saveJournal(Journal journal) {
        inTransaction(
                () -> {
                    insertJournal(journal);
                    journal.entries().forEach(this::insertJournalEntry);
                    insertMovementIdempotency(journal);
                });
    }

    private void createTables() {
        exec(
                """
                CREATE TABLE IF NOT EXISTS ledger_migrations(
                    version VARCHAR[16],
                    description VARCHAR[256],
                    PRIMARY KEY version
                )
                """);

        exec(
                """
                CREATE TABLE IF NOT EXISTS ledger_accounts(
                    account_id VARCHAR[36],
                    document_number VARCHAR[32],
                    branch_number VARCHAR[16],
                    account_number VARCHAR[32],
                    account_digit VARCHAR[8],
                    currency VARCHAR[3],
                    type VARCHAR[16],
                    created_at VARCHAR[40],
                    PRIMARY KEY account_id
                )
                """);

        exec(
                """
                CREATE TABLE IF NOT EXISTS ledger_journals(
                    movement_id VARCHAR[36],
                    transfer_id VARCHAR[36],
                    amount_minor INTEGER,
                    currency VARCHAR[3],
                    movement_type VARCHAR[32],
                    created_at VARCHAR[40],
                    PRIMARY KEY movement_id
                )
                """);

        exec(
                """
                CREATE TABLE IF NOT EXISTS ledger_journal_entries(
                    movement_id VARCHAR[36],
                    side VARCHAR[8],
                    account_id VARCHAR[36],
                    amount_minor INTEGER,
                    currency VARCHAR[3],
                    PRIMARY KEY (movement_id, side)
                )
                """);

        exec(
                """
                CREATE TABLE IF NOT EXISTS ledger_movement_idempotency(
                    movement_id VARCHAR[36],
                    posted_at VARCHAR[40],
                    PRIMARY KEY movement_id
                )
                """);
    }

    private void applyAccountTypeMigration() {
        if (!migrationExists(INITIAL_MIGRATION_VERSION)
                || migrationExists(ACCOUNT_TYPE_MIGRATION_VERSION)) {
            return;
        }

        exec("ALTER TABLE ledger_accounts ADD COLUMN type VARCHAR[16]");
        exec(
                """
                UPDATE ledger_accounts
                SET type = @type
                """,
                Map.of("type", new SQLValue("LIABILITY")));
    }

    private boolean migrationExists(String version) {
        return exists(
                "SELECT version FROM ledger_migrations WHERE version = @version",
                Map.of("version", new SQLValue(version)));
    }

    private boolean exists(String sql, Map<String, SQLValue> params) {
        SQLQueryResult result = query(sql, params);
        try {
            return result.next();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query immudb", exception);
        } finally {
            close(result);
        }
    }

    private void insertJournal(Journal journal) {
        exec(
                """
                INSERT INTO ledger_journals(
                    movement_id,
                    transfer_id,
                    amount_minor,
                    currency,
                    movement_type,
                    created_at
                )
                VALUES (
                    @movementId,
                    @transferId,
                    @amountMinor,
                    @currency,
                    @movementType,
                    @createdAt
                )
                """,
                Map.of(
                        "movementId", new SQLValue(journal.movementId().toString()),
                        "transferId", new SQLValue(journal.transferId().toString()),
                        "amountMinor", new SQLValue(journal.amountMinor()),
                        "currency", new SQLValue(journal.currency()),
                        "movementType", new SQLValue(journal.type()),
                        "createdAt", new SQLValue(journal.createdAt().toString())));
    }

    private void insertJournalEntry(JournalEntry entry) {
        exec(
                """
                INSERT INTO ledger_journal_entries(
                    movement_id,
                    side,
                    account_id,
                    amount_minor,
                    currency
                )
                VALUES (
                    @movementId,
                    @side,
                    @accountId,
                    @amountMinor,
                    @currency
                )
                """,
                Map.of(
                        "movementId", new SQLValue(entry.movementId().toString()),
                        "side", new SQLValue(entry.side().name()),
                        "accountId", new SQLValue(entry.accountId().toString()),
                        "amountMinor", new SQLValue(entry.amountMinor()),
                        "currency", new SQLValue(entry.currency())));
    }

    private void insertMovementIdempotency(Journal journal) {
        exec(
                """
                INSERT INTO ledger_movement_idempotency(movement_id, posted_at)
                VALUES (@movementId, @postedAt)
                """,
                Map.of(
                        "movementId", new SQLValue(journal.movementId().toString()),
                        "postedAt", new SQLValue(journal.createdAt().toString())));
    }

    private void exec(String sql) {
        try {
            immuClient.sqlExec(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to execute immudb statement", exception);
        }
    }

    private void exec(String sql, Map<String, SQLValue> params) {
        try {
            immuClient.sqlExec(sql, params);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to execute immudb statement", exception);
        }
    }

    private SQLQueryResult query(String sql, Map<String, SQLValue> params) {
        try {
            return immuClient.sqlQuery(sql, params);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query immudb", exception);
        }
    }

    private void close(SQLQueryResult result) {
        try {
            result.close();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to close immudb query result", exception);
        }
    }

    private void rollback() {
        try {
            immuClient.rollbackTransaction();
        } catch (SQLException rollbackException) {
            throw new IllegalStateException(
                    "Unable to rollback immudb transaction", rollbackException);
        }
    }

    private void inTransaction(SqlRunnable runnable) {
        inTransaction(
                () -> {
                    runnable.run();
                    return null;
                });
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private <T> T inTransaction(SqlSupplier<T> supplier) {
        try {
            immuClient.beginTransaction();
            T result = supplier.get();
            immuClient.commitTransaction();
            return result;
        } catch (SQLException exception) {
            rollback();
            throw new IllegalStateException("Unable to execute immudb transaction", exception);
        } catch (RuntimeException exception) {
            rollback();
            throw exception;
        }
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}

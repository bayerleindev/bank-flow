package br.com.bankflow.accounts.shared.repository;

import br.com.bankflow.accounts.shared.domain.Account;
import br.com.bankflow.accounts.shared.domain.AccountStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("PMD.TooManyMethods")
public class AccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Account create(Account account) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
				insert into accounts.accounts (
					id, full_name, document_number, email, mother_name, social_name,
					phone_number, birth_date, address, is_politically_exposed, status,
					onboarding_application_id, credentials_id, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::accounts.account_status, ?, ?, ?, ?)
				""",
                account.id(),
                account.fullName(),
                account.documentNumber(),
                account.email(),
                account.motherName(),
                account.socialName(),
                account.phoneNumber(),
                account.birthDate(),
                account.address(),
                account.politicallyExposed(),
                account.status().name(),
                account.onboardingApplicationId(),
                account.credentialsId(),
                Timestamp.from(now),
                Timestamp.from(now));

        return findById(account.id()).orElseThrow();
    }

    public Optional<Account> findById(UUID accountId) {
        return jdbcTemplate
                .query(
                        """
				select id, full_name, document_number, email, mother_name, social_name,
					phone_number, birth_date, address, is_politically_exposed, status,
					branch_number, account_number, account_digit, rejection_reason,
					onboarding_application_id, credentials_id,
					created_at, updated_at
				from accounts.accounts
				where id = ?
				""",
                        rowMapper(),
                        accountId)
                .stream()
                .findFirst();
    }

    public Optional<Account> findActiveByBranchAccountAndDigit(
            String branchNumber, String accountNumber, String accountDigit) {
        return jdbcTemplate
                .query(
                        """
					select id, full_name, document_number, email, mother_name, social_name,
						phone_number, birth_date, address, is_politically_exposed, status,
						branch_number, account_number, account_digit, rejection_reason,
						onboarding_application_id, credentials_id,
						created_at, updated_at
					from accounts.accounts
					where branch_number = ?
						and account_number = ?
						and account_digit = ?
						and status = ?::accounts.account_status
					""",
                        rowMapper(),
                        branchNumber,
                        accountNumber,
                        accountDigit,
                        AccountStatus.ACTIVE.name())
                .stream()
                .findFirst();
    }

    public Optional<Account> findByDocumentNumber(String documentNumber) {
        return jdbcTemplate
                .query(
                        """
				select id, full_name, document_number, email, mother_name, social_name,
					phone_number, birth_date, address, is_politically_exposed, status,
					branch_number, account_number, account_digit, rejection_reason,
					onboarding_application_id, credentials_id,
					created_at, updated_at
				from accounts.accounts
				where document_number = ?
				""",
                        rowMapper(),
                        documentNumber)
                .stream()
                .findFirst();
    }

    public Optional<Account> findByOnboardingApplicationId(UUID onboardingApplicationId) {
        return jdbcTemplate
                .query(
                        """
				select id, full_name, document_number, email, mother_name, social_name,
					phone_number, birth_date, address, is_politically_exposed, status,
					branch_number, account_number, account_digit, rejection_reason,
					onboarding_application_id, credentials_id,
					created_at, updated_at
				from accounts.accounts
				where onboarding_application_id = ?
				""",
                        rowMapper(),
                        onboardingApplicationId)
                .stream()
                .findFirst();
    }

    public Optional<Account> markPendingBaas(UUID accountId) {
        jdbcTemplate.update(
                """
				update accounts.accounts
				set status = ?::accounts.account_status,
					updated_at = now()
				where id = ?
				""",
                AccountStatus.PENDING_BAAS.name(),
                accountId);

        return findById(accountId);
    }

    public Optional<Account> activate(
            UUID accountId, String branchNumber, String accountNumber, String accountDigit) {
        jdbcTemplate.update(
                """
				update accounts.accounts
				set status = ?::accounts.account_status,
					branch_number = ?,
					account_number = ?,
					account_digit = ?,
					rejection_reason = null,
					updated_at = now()
				where id = ?
				""",
                AccountStatus.ACTIVE.name(),
                branchNumber,
                accountNumber,
                accountDigit,
                accountId);

        return findById(accountId);
    }

    public Optional<Account> reject(UUID accountId, String rejectionReason) {
        jdbcTemplate.update(
                """
				update accounts.accounts
				set status = ?::accounts.account_status,
					rejection_reason = ?,
					updated_at = now()
				where id = ?
				""",
                AccountStatus.REJECTED.name(),
                rejectionReason,
                accountId);

        return findById(accountId);
    }

    private RowMapper<Account> rowMapper() {
        return (rs, rowNum) ->
                new Account(
                        rs.getObject("id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("document_number"),
                        rs.getString("email"),
                        rs.getString("mother_name"),
                        rs.getString("social_name"),
                        rs.getString("phone_number"),
                        localDate(rs, "birth_date"),
                        rs.getString("address"),
                        rs.getBoolean("is_politically_exposed"),
                        AccountStatus.valueOf(rs.getString("status")),
                        rs.getString("branch_number"),
                        rs.getString("account_number"),
                        rs.getString("account_digit"),
                        rs.getString("rejection_reason"),
                        rs.getObject("onboarding_application_id", UUID.class),
                        rs.getObject("credentials_id", UUID.class),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at"));
    }

    private LocalDate localDate(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}

package br.com.bankflow.auth.repository;

import br.com.bankflow.auth.service.AccountLink;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AccountLinkRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(AccountLink accountLink) {
        jdbcTemplate.update(
                """
				insert into auth.account_links (
					document_number, account_id, branch_number, account_number, account_digit,
					created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?)
				on conflict (document_number) do update set
					account_id = excluded.account_id,
					branch_number = excluded.branch_number,
					account_number = excluded.account_number,
					account_digit = excluded.account_digit,
					updated_at = excluded.updated_at
				""",
                accountLink.documentNumber(),
                accountLink.accountId(),
                accountLink.branchNumber(),
                accountLink.accountNumber(),
                accountLink.accountDigit(),
                Timestamp.from(accountLink.createdAt()),
                Timestamp.from(accountLink.updatedAt()));
    }

    public Optional<AccountLink> findByDocumentNumber(String documentNumber) {
        return jdbcTemplate
                .query(
                        """
				select document_number, account_id, branch_number, account_number, account_digit,
					created_at, updated_at
				from auth.account_links
				where document_number = ?
				""",
                        rowMapper(),
                        documentNumber)
                .stream()
                .findFirst();
    }

    public Optional<UUID> findAccountIdByDocumentNumber(String documentNumber) {
        return findByDocumentNumber(documentNumber).map(AccountLink::accountId);
    }

    private RowMapper<AccountLink> rowMapper() {
        return (rs, rowNum) ->
                new AccountLink(
                        rs.getString("document_number"),
                        rs.getObject("account_id", UUID.class),
                        rs.getString("branch_number"),
                        rs.getString("account_number"),
                        rs.getString("account_digit"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant());
    }
}

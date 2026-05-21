package br.com.bankflow.auth.repository;

import br.com.bankflow.auth.service.Credential;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CredentialRepository {

    private final JdbcTemplate jdbcTemplate;

    public CredentialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Credential create(Credential credential) {
        jdbcTemplate.update(
                """
				insert into auth.credentials (
					id, onboarding_application_id, document_number, password_hash, status,
					created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?)
				""",
                credential.id(),
                credential.onboardingApplicationId(),
                credential.documentNumber(),
                credential.passwordHash(),
                credential.status(),
                Timestamp.from(credential.createdAt()),
                Timestamp.from(credential.updatedAt()));
        return findById(credential.id()).orElseThrow();
    }

    public Optional<Credential> findById(UUID id) {
        return jdbcTemplate
                .query(
                        """
				select id, onboarding_application_id, document_number, password_hash, status,
					created_at, updated_at
				from auth.credentials
				where id = ?
				""",
                        rowMapper(),
                        id)
                .stream()
                .findFirst();
    }

    public Optional<Credential> findByDocumentNumber(String documentNumber) {
        return jdbcTemplate
                .query(
                        """
				select id, onboarding_application_id, document_number, password_hash, status,
					created_at, updated_at
				from auth.credentials
				where document_number = ?
				""",
                        rowMapper(),
                        documentNumber)
                .stream()
                .findFirst();
    }

    private RowMapper<Credential> rowMapper() {
        return (rs, rowNum) ->
                new Credential(
                        rs.getObject("id", UUID.class),
                        rs.getObject("onboarding_application_id", UUID.class),
                        rs.getString("document_number"),
                        rs.getString("password_hash"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant());
    }
}

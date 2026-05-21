package br.com.bankflow.onboarding.api.repository;

import br.com.bankflow.onboarding.api.service.ApplicationStatus;
import br.com.bankflow.onboarding.api.service.OnboardingApplication;
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
public class OnboardingApplicationRepository {

    private final JdbcTemplate jdbcTemplate;

    public OnboardingApplicationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OnboardingApplication create(OnboardingApplication application) {
        jdbcTemplate.update(
                """
				insert into onboarding.onboarding_applications (
					id, status, full_name, document_number, email, mother_name, social_name,
					phone_number, birth_date, address, is_politically_exposed,
					application_token_hash, application_token_expires_at, created_at, updated_at
				) values (?, ?::onboarding.application_status, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
                application.id(),
                application.status().name(),
                application.fullName(),
                application.documentNumber(),
                application.email(),
                application.motherName(),
                application.socialName(),
                application.phoneNumber(),
                application.birthDate(),
                application.address(),
                application.politicallyExposed(),
                application.applicationTokenHash(),
                Timestamp.from(application.applicationTokenExpiresAt()),
                Timestamp.from(application.createdAt()),
                Timestamp.from(application.updatedAt()));
        return findById(application.id()).orElseThrow();
    }

    public Optional<OnboardingApplication> findById(UUID applicationId) {
        return jdbcTemplate
                .query(
                        """
				select id, status, full_name, document_number, email, mother_name, social_name,
					phone_number, birth_date, address, is_politically_exposed, credentials_id,
					rejection_reason_code, application_token_hash, application_token_expires_at,
					created_at, updated_at, submitted_at, approved_at, rejected_at
				from onboarding.onboarding_applications
				where id = ?
				""",
                        rowMapper(),
                        applicationId)
                .stream()
                .findFirst();
    }

    public Optional<OnboardingApplication> findByTokenHash(String applicationTokenHash) {
        return jdbcTemplate
                .query(
                        """
				select id, status, full_name, document_number, email, mother_name, social_name,
					phone_number, birth_date, address, is_politically_exposed, credentials_id,
					rejection_reason_code, application_token_hash, application_token_expires_at,
					created_at, updated_at, submitted_at, approved_at, rejected_at
				from onboarding.onboarding_applications
				where application_token_hash = ?
				""",
                        rowMapper(),
                        applicationTokenHash)
                .stream()
                .findFirst();
    }

    public Optional<OnboardingApplication> updateStatus(
            UUID applicationId, ApplicationStatus status) {
        jdbcTemplate.update(
                """
				update onboarding.onboarding_applications
				set status = ?::onboarding.application_status,
					updated_at = now()
				where id = ?
				""",
                status.name(),
                applicationId);
        return findById(applicationId);
    }

    public Optional<OnboardingApplication> markCredentialsCreated(
            UUID applicationId, UUID credentialsId) {
        jdbcTemplate.update(
                """
				update onboarding.onboarding_applications
				set status = ?::onboarding.application_status,
					credentials_id = ?,
					updated_at = now()
				where id = ?
				""",
                ApplicationStatus.CREDENTIALS_CREATED.name(),
                credentialsId,
                applicationId);
        return findById(applicationId);
    }

    public Optional<OnboardingApplication> markSubmitted(UUID applicationId) {
        jdbcTemplate.update(
                """
				update onboarding.onboarding_applications
				set status = ?::onboarding.application_status,
					submitted_at = now(),
					updated_at = now()
				where id = ?
				""",
                ApplicationStatus.UNDER_REVIEW.name(),
                applicationId);
        return findById(applicationId);
    }

    public Optional<OnboardingApplication> approve(UUID applicationId) {
        jdbcTemplate.update(
                """
				update onboarding.onboarding_applications
				set status = ?::onboarding.application_status,
					approved_at = now(),
					rejection_reason_code = null,
					updated_at = now()
				where id = ?
				""",
                ApplicationStatus.APPROVED.name(),
                applicationId);
        return findById(applicationId);
    }

    public Optional<OnboardingApplication> reject(UUID applicationId, String reasonCode) {
        jdbcTemplate.update(
                """
				update onboarding.onboarding_applications
				set status = ?::onboarding.application_status,
					rejected_at = now(),
					rejection_reason_code = ?,
					updated_at = now()
				where id = ?
				""",
                ApplicationStatus.REJECTED.name(),
                reasonCode,
                applicationId);
        return findById(applicationId);
    }

    private RowMapper<OnboardingApplication> rowMapper() {
        return (rs, rowNum) ->
                new OnboardingApplication(
                        rs.getObject("id", UUID.class),
                        ApplicationStatus.valueOf(rs.getString("status")),
                        rs.getString("full_name"),
                        rs.getString("document_number"),
                        rs.getString("email"),
                        rs.getString("mother_name"),
                        rs.getString("social_name"),
                        rs.getString("phone_number"),
                        localDate(rs, "birth_date"),
                        rs.getString("address"),
                        rs.getBoolean("is_politically_exposed"),
                        rs.getObject("credentials_id", UUID.class),
                        rs.getString("rejection_reason_code"),
                        rs.getString("application_token_hash"),
                        instant(rs, "application_token_expires_at"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at"),
                        instant(rs, "submitted_at"),
                        instant(rs, "approved_at"),
                        instant(rs, "rejected_at"));
    }

    private LocalDate localDate(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}

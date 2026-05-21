package br.com.bankflow.onboarding.api.repository;

import br.com.bankflow.onboarding.api.service.DocumentStatus;
import br.com.bankflow.onboarding.api.service.DocumentType;
import br.com.bankflow.onboarding.api.service.OnboardingDocument;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OnboardingDocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public OnboardingDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OnboardingDocument create(OnboardingDocument document) {
        jdbcTemplate.update(
                """
				insert into onboarding.onboarding_documents (
					id, application_id, type, status, storage_key, content_type,
					content_length, content_hash, created_at, updated_at
				) values (?, ?, ?::onboarding.document_type, ?::onboarding.document_status,
					?, ?, ?, ?, ?, ?)
				""",
                document.id(),
                document.applicationId(),
                document.type().name(),
                document.status().name(),
                document.storageKey(),
                document.contentType(),
                document.contentLength(),
                document.contentHash(),
                Timestamp.from(document.createdAt()),
                Timestamp.from(document.updatedAt()));
        return findById(document.id()).orElseThrow();
    }

    public Optional<OnboardingDocument> findById(UUID documentId) {
        return jdbcTemplate
                .query(
                        """
				select id, application_id, type, status, storage_key, content_type, content_length,
					content_hash, rejection_reason_code, created_at, updated_at, uploaded_at
				from onboarding.onboarding_documents
				where id = ?
				""",
                        rowMapper(),
                        documentId)
                .stream()
                .findFirst();
    }

    public List<OnboardingDocument> findByApplicationId(UUID applicationId) {
        return jdbcTemplate.query(
                """
				select id, application_id, type, status, storage_key, content_type, content_length,
					content_hash, rejection_reason_code, created_at, updated_at, uploaded_at
				from onboarding.onboarding_documents
				where application_id = ?
				order by created_at
				""",
                rowMapper(),
                applicationId);
    }

    public OnboardingDocument markUploaded(
            UUID documentId, Long contentLength, String contentHash) {
        jdbcTemplate.update(
                """
				update onboarding.onboarding_documents
				set status = ?::onboarding.document_status,
					content_length = ?,
					content_hash = ?,
					uploaded_at = now(),
					updated_at = now()
				where id = ?
				""",
                DocumentStatus.UPLOADED.name(),
                contentLength,
                contentHash,
                documentId);
        return findById(documentId).orElseThrow();
    }

    private RowMapper<OnboardingDocument> rowMapper() {
        return (rs, rowNum) ->
                new OnboardingDocument(
                        rs.getObject("id", UUID.class),
                        rs.getObject("application_id", UUID.class),
                        DocumentType.valueOf(rs.getString("type")),
                        DocumentStatus.valueOf(rs.getString("status")),
                        rs.getString("storage_key"),
                        rs.getString("content_type"),
                        longObject(rs, "content_length"),
                        rs.getString("content_hash"),
                        rs.getString("rejection_reason_code"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at"),
                        instant(rs, "uploaded_at"));
    }

    private Long longObject(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}

package br.com.bankflow.onboarding.api.dto;

import br.com.bankflow.onboarding.api.service.DocumentStatus;
import br.com.bankflow.onboarding.api.service.DocumentType;
import br.com.bankflow.onboarding.api.service.OnboardingDocument;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID applicationId,
        DocumentType type,
        DocumentStatus status,
        String storageKey,
        String contentType,
        Long contentLength,
        String contentHash,
        Instant createdAt,
        Instant uploadedAt) {

    public static DocumentResponse from(OnboardingDocument document) {
        return new DocumentResponse(
                document.id(),
                document.applicationId(),
                document.type(),
                document.status(),
                document.storageKey(),
                document.contentType(),
                document.contentLength(),
                document.contentHash(),
                document.createdAt(),
                document.uploadedAt());
    }
}

package br.com.bankflow.onboarding.api.dto;

import br.com.bankflow.onboarding.api.service.DocumentStatus;
import br.com.bankflow.onboarding.api.service.DocumentType;
import java.time.Instant;
import java.util.UUID;

public record CreateDocumentUploadResponse(
        UUID documentId,
        DocumentType type,
        DocumentStatus status,
        String storageKey,
        String uploadUrl,
        Instant uploadUrlExpiresAt) {}

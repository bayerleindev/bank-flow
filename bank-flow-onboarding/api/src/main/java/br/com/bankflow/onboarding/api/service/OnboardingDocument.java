package br.com.bankflow.onboarding.api.service;

import java.time.Instant;
import java.util.UUID;

public record OnboardingDocument(
        UUID id,
        UUID applicationId,
        DocumentType type,
        DocumentStatus status,
        String storageKey,
        String contentType,
        Long contentLength,
        String contentHash,
        String rejectionReasonCode,
        Instant createdAt,
        Instant updatedAt,
        Instant uploadedAt) {}

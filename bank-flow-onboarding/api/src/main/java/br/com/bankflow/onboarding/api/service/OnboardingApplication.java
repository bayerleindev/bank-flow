package br.com.bankflow.onboarding.api.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OnboardingApplication(
        UUID id,
        ApplicationStatus status,
        String fullName,
        String documentNumber,
        String email,
        String motherName,
        String socialName,
        String phoneNumber,
        LocalDate birthDate,
        String address,
        boolean politicallyExposed,
        UUID credentialsId,
        String rejectionReasonCode,
        String applicationTokenHash,
        Instant applicationTokenExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        Instant approvedAt,
        Instant rejectedAt) {}

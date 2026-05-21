package br.com.bankflow.onboarding.api.dto;

import br.com.bankflow.onboarding.api.service.ApplicationStatus;
import br.com.bankflow.onboarding.api.service.OnboardingApplication;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicationResponse(
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
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        Instant approvedAt,
        Instant rejectedAt) {

    public static ApplicationResponse from(OnboardingApplication application) {
        return new ApplicationResponse(
                application.id(),
                application.status(),
                application.fullName(),
                application.documentNumber(),
                application.email(),
                application.motherName(),
                application.socialName(),
                application.phoneNumber(),
                application.birthDate(),
                application.address(),
                application.politicallyExposed(),
                application.credentialsId(),
                application.rejectionReasonCode(),
                application.createdAt(),
                application.updatedAt(),
                application.submittedAt(),
                application.approvedAt(),
                application.rejectedAt());
    }
}

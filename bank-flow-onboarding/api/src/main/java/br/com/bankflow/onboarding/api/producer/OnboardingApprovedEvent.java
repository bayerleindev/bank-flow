package br.com.bankflow.onboarding.api.producer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OnboardingApprovedEvent(
        UUID applicationId,
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
        Instant approvedAt) {}

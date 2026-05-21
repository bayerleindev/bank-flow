package br.com.bankflow.accounts.shared.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Account(
        UUID id,
        String fullName,
        String documentNumber,
        String email,
        String motherName,
        String socialName,
        String phoneNumber,
        LocalDate birthDate,
        String address,
        boolean politicallyExposed,
        AccountStatus status,
        String branchNumber,
        String accountNumber,
        String accountDigit,
        String rejectionReason,
        UUID onboardingApplicationId,
        UUID credentialsId,
        Instant createdAt,
        Instant updatedAt) {}

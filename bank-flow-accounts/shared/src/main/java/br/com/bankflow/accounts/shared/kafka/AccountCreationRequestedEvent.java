package br.com.bankflow.accounts.shared.kafka;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountCreationRequestedEvent(
        UUID onboardingApplicationId,
        UUID credentialsId,
        String fullName,
        String documentNumber,
        String email,
        String motherName,
        String socialName,
        String phoneNumber,
        LocalDate birthDate,
        String address,
        boolean politicallyExposed,
        Instant requestedAt) {}

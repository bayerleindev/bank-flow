package br.com.bankflow.auth.service;

import java.time.Instant;
import java.util.UUID;

public record Credential(
        UUID id,
        UUID onboardingApplicationId,
        String documentNumber,
        String passwordHash,
        String status,
        Instant createdAt,
        Instant updatedAt) {}

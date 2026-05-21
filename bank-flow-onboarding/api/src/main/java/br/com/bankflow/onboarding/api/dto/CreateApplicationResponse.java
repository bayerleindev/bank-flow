package br.com.bankflow.onboarding.api.dto;

import br.com.bankflow.onboarding.api.service.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

public record CreateApplicationResponse(
        UUID id, ApplicationStatus status, String applicationToken, Instant tokenExpiresAt) {}

package br.com.bankflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCredentialsRequest(
        @NotNull UUID onboardingApplicationId,
        @NotBlank String documentNumber,
        @NotBlank String password) {}

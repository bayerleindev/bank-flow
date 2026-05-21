package br.com.bankflow.onboarding.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectApplicationRequest(@NotBlank String reasonCode) {}

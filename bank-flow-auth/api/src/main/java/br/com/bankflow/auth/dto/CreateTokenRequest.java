package br.com.bankflow.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTokenRequest(@NotBlank String documentNumber, @NotBlank String password) {}

package br.com.bankflow.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTokenRequest(@NotNull UUID accountId) {}

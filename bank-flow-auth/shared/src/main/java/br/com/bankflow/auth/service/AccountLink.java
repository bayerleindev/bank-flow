package br.com.bankflow.auth.service;

import java.time.Instant;
import java.util.UUID;

public record AccountLink(
        String documentNumber,
        UUID accountId,
        String branchNumber,
        String accountNumber,
        String accountDigit,
        Instant createdAt,
        Instant updatedAt) {}

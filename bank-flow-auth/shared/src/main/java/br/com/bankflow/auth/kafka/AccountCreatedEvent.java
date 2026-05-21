package br.com.bankflow.auth.kafka;

import java.util.UUID;

public record AccountCreatedEvent(
        UUID accountId,
        String documentNumber,
        String branchNumber,
        String accountNumber,
        String accountDigit) {}

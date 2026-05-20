package br.com.bankflow.accounts.shared.kafka;

import java.util.UUID;

public record AccountCreatedEvent(
        UUID accountId,
        String documentNumber,
        String branchNumber,
        String accountNumber,
        String accountDigit) {}

package br.com.bankflow.ledger.kafka;

import java.util.UUID;

public record AccountCreatedEvent(
        UUID accountId,
        String documentNumber,
        String branchNumber,
        String accountNumber,
        String accountDigit) {}

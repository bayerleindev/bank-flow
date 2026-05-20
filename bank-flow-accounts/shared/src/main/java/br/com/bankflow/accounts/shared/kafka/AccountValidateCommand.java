package br.com.bankflow.accounts.shared.kafka;

import java.time.Instant;
import java.util.UUID;

public record AccountValidateCommand(
        UUID transferId,
        UUID debitAccountId,
        TransferParty creditParty,
        String idempotencyKey,
        Instant requestedAt) {}

package br.com.bankflow.accounts.shared.kafka;

import java.time.Instant;
import java.util.UUID;

public record AccountValidateCommand(
        UUID transferId,
        TransferParty debitParty,
        TransferParty creditParty,
        String idempotencyKey,
        Instant requestedAt) {}

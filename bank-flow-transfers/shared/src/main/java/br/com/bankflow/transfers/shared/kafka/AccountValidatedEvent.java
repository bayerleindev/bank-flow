package br.com.bankflow.transfers.shared.kafka;

import br.com.bankflow.transfers.shared.domain.TransferParty;
import java.time.Instant;
import java.util.UUID;

public record AccountValidatedEvent(
        UUID transferId,
        AccountValidationStatus status,
        String reason,
        UUID debitAccountId,
        TransferParty debitParty,
        UUID creditAccountId,
        Instant validatedAt) {}

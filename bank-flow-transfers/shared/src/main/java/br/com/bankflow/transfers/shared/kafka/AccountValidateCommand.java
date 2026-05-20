package br.com.bankflow.transfers.shared.kafka;

import br.com.bankflow.transfers.shared.domain.TransferParty;
import java.time.Instant;
import java.util.UUID;

public record AccountValidateCommand(
        UUID transferId,
        UUID debitAccountId,
        TransferParty creditParty,
        String idempotencyKey,
        Instant requestedAt) {}

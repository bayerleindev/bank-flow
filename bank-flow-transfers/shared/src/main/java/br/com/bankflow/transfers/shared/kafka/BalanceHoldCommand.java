package br.com.bankflow.transfers.shared.kafka;

import br.com.bankflow.transfers.shared.domain.TransferParty;
import java.time.Instant;
import java.util.UUID;

public record BalanceHoldCommand(
        UUID transferId,
        UUID debitAccountId,
        TransferParty debitParty,
        long amountMinor,
        String currency,
        String idempotencyKey,
        Instant requestedAt) {}

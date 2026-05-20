package br.com.bankflow.transfers.api.service;

import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import java.util.UUID;

public record CreateTransferCommand(
        UUID debitAccountId,
        TransferParty creditParty,
        String idempotencyKey,
        long amountMinor,
        String description,
        String currency,
        TransferType type) {}

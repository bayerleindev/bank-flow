package br.com.bankflow.transfers.api.service;

import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;

public record CreateTransferCommand(
        TransferParty debitParty,
        TransferParty creditParty,
        String idempotencyKey,
        long amountMinor,
        String description,
        String currency,
        TransferType type) {}

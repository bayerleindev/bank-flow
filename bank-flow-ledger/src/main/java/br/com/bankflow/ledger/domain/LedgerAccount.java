package br.com.bankflow.ledger.domain;

import java.time.Instant;
import java.util.UUID;

public record LedgerAccount(
        UUID accountId,
        String documentNumber,
        String branchNumber,
        String accountNumber,
        String accountDigit,
        String currency,
        LedgerAccountType type,
        Instant createdAt) {}

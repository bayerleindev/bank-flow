package br.com.bankflow.transfers.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BaasTransferWebhookRequest(
        @NotNull UUID transferId, @NotNull BaasTransferWebhookStatus status, String reason) {}

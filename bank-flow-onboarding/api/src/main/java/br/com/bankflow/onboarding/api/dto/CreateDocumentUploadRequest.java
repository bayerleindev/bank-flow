package br.com.bankflow.onboarding.api.dto;

import br.com.bankflow.onboarding.api.service.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDocumentUploadRequest(
        @NotNull DocumentType type, @NotBlank String contentType) {}

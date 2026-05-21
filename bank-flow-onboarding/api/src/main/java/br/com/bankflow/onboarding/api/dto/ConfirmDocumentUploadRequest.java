package br.com.bankflow.onboarding.api.dto;

import jakarta.validation.constraints.Positive;

public record ConfirmDocumentUploadRequest(@Positive Long contentLength, String contentHash) {}

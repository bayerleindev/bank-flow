package br.com.bankflow.accounts.worker.client;

import java.util.UUID;

public record BaasAccountCreationRequest(
        UUID accountId,
        String fullName,
        String documentNumber,
        String email,
        String motherName,
        String socialName,
        String phoneNumber,
        String birthDate,
        String address,
        boolean politicallyExposed) {}

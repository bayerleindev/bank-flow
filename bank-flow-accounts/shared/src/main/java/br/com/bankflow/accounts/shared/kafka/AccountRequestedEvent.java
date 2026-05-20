package br.com.bankflow.accounts.shared.kafka;

import java.time.LocalDate;
import java.util.UUID;

public record AccountRequestedEvent(
        UUID accountId,
        String fullName,
        String documentNumber,
        String email,
        String motherName,
        String socialName,
        String phoneNumber,
        LocalDate birthDate,
        String address,
        boolean politicallyExposed) {}

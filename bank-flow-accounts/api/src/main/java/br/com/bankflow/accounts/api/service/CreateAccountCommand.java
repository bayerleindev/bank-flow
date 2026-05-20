package br.com.bankflow.accounts.api.service;

import br.com.bankflow.accounts.api.dto.request.CreateAccountRequest;
import java.time.LocalDate;

public record CreateAccountCommand(
        String fullName,
        String documentNumber,
        String email,
        String motherName,
        String socialName,
        String phoneNumber,
        LocalDate birthDate,
        String address,
        boolean politicallyExposed) {

    public static CreateAccountCommand from(CreateAccountRequest request) {
        return new CreateAccountCommand(
                request.fullName(),
                request.documentNumber(),
                request.email(),
                request.motherName(),
                request.socialName(),
                request.phoneNumber(),
                request.birthDate(),
                request.address(),
                request.isPoliticallyExposed());
    }
}

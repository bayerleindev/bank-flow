package br.com.bankflow.accounts.api.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateAccountRequest(
        @NotBlank String fullName,
        @NotBlank String documentNumber,
        @NotBlank @Email String email,
        @NotBlank String motherName,
        String socialName,
        @NotBlank String phoneNumber,
        @NotNull @JsonFormat(pattern = "dd-MM-yyyy") LocalDate birthDate,
        @NotBlank String address,
        boolean isPoliticallyExposed) {}

package br.com.bankflow.onboarding.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateApplicationRequest(
        @NotBlank String fullName,
        @NotBlank String documentNumber,
        @NotBlank @Email String email,
        @NotBlank String motherName,
        String socialName,
        @NotBlank String phoneNumber,
        @NotNull @JsonFormat(pattern = "dd-MM-yyyy") LocalDate birthDate,
        @NotBlank String address,
        boolean isPoliticallyExposed) {}

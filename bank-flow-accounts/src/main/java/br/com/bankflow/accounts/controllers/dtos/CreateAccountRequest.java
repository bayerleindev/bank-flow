package br.com.bankflow.accounts.controllers.dtos;

import br.com.bankflow.accounts.domain.CreateAccountCommand;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record CreateAccountRequest(
		String fullName,
		String documentNumber,
		String email,
		String motherName,
		String socialName,
		String phoneNumber,
		@JsonFormat(pattern = "dd-MM-yyyy") LocalDate birthDate,
		String address,
		@JsonProperty("isPoliticallyExposed") boolean politicallyExposed
) {
	public CreateAccountCommand toCommand(String idempotencyKey) {
		return new CreateAccountCommand(
				idempotencyKey,
				fullName,
				documentNumber,
				email,
				motherName,
				socialName,
				phoneNumber,
				birthDate,
				address,
				politicallyExposed
		);
	}
}

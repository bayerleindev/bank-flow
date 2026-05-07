package br.com.bankflow.accounts.clients.baas;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BaasCreateAccountRequest(
		String fullName,
		String documentNumber,
		String email,
		String motherName,
		String socialName,
		String phoneNumber,
		String birthDate,
		String address,
		@JsonProperty("isPoliticallyExposed") boolean politicallyExposed
) {
}

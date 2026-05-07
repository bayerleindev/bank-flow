package br.com.bankflow.accounts.domain;

import java.time.LocalDate;

public record CreateAccountCommand(
		String idempotencyKey,
		String fullName,
		String documentNumber,
		String email,
		String motherName,
		String socialName,
		String phoneNumber,
		LocalDate birthDate,
		String address,
		boolean politicallyExposed
) {
	public void validate() {
		if (isBlank(idempotencyKey)) {
			throw new IllegalArgumentException("Idempotency-Key is required");
		}
		if (isBlank(fullName)) {
			throw new IllegalArgumentException("fullName is required");
		}
		if (!isValidCpf(documentNumber)) {
			throw new IllegalArgumentException("documentNumber must be a valid CPF");
		}
		if (isBlank(email) || !email.contains("@")) {
			throw new IllegalArgumentException("email is invalid");
		}
		if (isBlank(motherName)) {
			throw new IllegalArgumentException("motherName is required");
		}
		if (isBlank(phoneNumber)) {
			throw new IllegalArgumentException("phoneNumber is required");
		}
		if (birthDate == null) {
			throw new IllegalArgumentException("birthDate is required");
		}
		if (isBlank(address)) {
			throw new IllegalArgumentException("address is required");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static boolean isValidCpf(String value) {
		if (value == null) {
			return false;
		}
		String cpf = value.replaceAll("\\D", "");
		if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
			return false;
		}
		return digit(cpf, 9) == cpf.charAt(9) - '0'
				&& digit(cpf, 10) == cpf.charAt(10) - '0';
	}

	private static int digit(String cpf, int length) {
		int sum = 0;
		for (int index = 0; index < length; index++) {
			sum += (cpf.charAt(index) - '0') * (length + 1 - index);
		}
		int result = 11 - (sum % 11);
		return result >= 10 ? 0 : result;
	}
}

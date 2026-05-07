package br.com.bankflow.accounts.domain;

import java.time.LocalDate;
import java.util.UUID;

public record Account(
		UUID accountId,
		String idempotencyKey,
		UUID ownerId,
		String fullName,
		String documentNumber,
		String email,
		String motherName,
		String socialName,
		String phoneNumber,
		LocalDate birthDate,
		String address,
		boolean politicallyExposed,
		String baasAccountId,
		String branch,
		String account,
		String currency,
		AccountStatus status,
		String failureReason,
		long createdAt,
		long updatedAt
) {
}

package br.com.bankflow.accounts.controllers.dtos;

import br.com.bankflow.accounts.domain.Account;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AccountResponse(
		@JsonProperty("account_id") String accountId,
		@JsonProperty("owner_id") String ownerId,
		@JsonProperty("document_number") String documentNumber,
		String email,
		@JsonProperty("baas_account_id") String baasAccountId,
		String branch,
		String account,
		String currency,
		String status,
		@JsonProperty("failure_reason") String failureReason,
		@JsonProperty("created_at") long createdAt,
		@JsonProperty("updated_at") long updatedAt
) {
	public static AccountResponse from(Account account) {
		return new AccountResponse(
				account.accountId().toString(),
				account.ownerId().toString(),
				maskDocument(account.documentNumber()),
				account.email(),
				account.baasAccountId(),
				account.branch(),
				account.account(),
				account.currency(),
				account.status().name(),
				account.failureReason(),
				account.createdAt(),
				account.updatedAt()
		);
	}

	private static String maskDocument(String documentNumber) {
		if (documentNumber == null || documentNumber.length() < 5) {
			return documentNumber;
		}
		return "***" + documentNumber.substring(documentNumber.length() - 4);
	}
}

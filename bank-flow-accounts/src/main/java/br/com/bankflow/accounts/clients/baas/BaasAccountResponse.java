package br.com.bankflow.accounts.clients.baas;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BaasAccountResponse(
		@JsonProperty("baas_account_id") String baasAccountId,
		String branch,
		String account,
		String currency,
		BaasAccountStatus status,
		@JsonProperty("failure_reason") String failureReason
) {
}

package br.com.bankflow.yielding.domain;

import java.util.UUID;

public record EligibleBalance(
		UUID digitalAccountId,
		String currency,
		long postedMinor
) {
}

package br.com.bankflow.accounts.clients.baas;

import br.com.bankflow.accounts.domain.CreateAccountCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank-flow.baas.mode", havingValue = "mock", matchIfMissing = true)
public class MockBaasClient implements BaasClient {
	@Override
	public BaasAccountResponse createAccount(CreateAccountCommand command) {
		String digits = command.documentNumber().replaceAll("\\D", "");
		String suffix = digits.substring(digits.length() - 5);
		return new BaasAccountResponse(
				"baas-" + digits,
				"0001",
				suffix + "-0",
				"BRL",
				BaasAccountStatus.ACTIVE,
				null
		);
	}
}

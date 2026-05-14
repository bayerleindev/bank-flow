package br.com.bankflow.transfer.clients.accounts;

import br.com.bankflow.transfer.resilience.HttpResilience;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class HttpAccountClient implements AccountClient {
	private final RestClient restClient;
	private final HttpResilience httpResilience;

	public HttpAccountClient(
			RestClient.Builder restClientBuilder,
			@Value("${bank-flow.accounts.base-url}") String accountsBaseUrl,
			HttpResilience httpResilience
	) {
		this.restClient = restClientBuilder.baseUrl(accountsBaseUrl).build();
		this.httpResilience = httpResilience;
	}

	@Override
	public AccountResponse getAccount(UUID digitalAccountId) {
		return httpResilience.execute("accounts.getAccount", () -> restClient.get()
				.uri("/accounts/{digital_account_id}", digitalAccountId)
				.retrieve()
				.body(AccountResponse.class));
	}
}

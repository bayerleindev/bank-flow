package br.com.bankflow.transfer.clients.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class HttpAccountClient implements AccountClient {
	private final RestClient restClient;

	public HttpAccountClient(
			RestClient.Builder restClientBuilder,
			@Value("${bank-flow.accounts.base-url}") String accountsBaseUrl
	) {
		this.restClient = restClientBuilder.baseUrl(accountsBaseUrl).build();
	}

	@Override
	public AccountResponse getAccount(UUID digitalAccountId) {
		return restClient.get()
				.uri("/accounts/{digital_account_id}", digitalAccountId)
				.retrieve()
				.body(AccountResponse.class);
	}
}

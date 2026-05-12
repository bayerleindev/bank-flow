package br.com.bankflow.transfer.clients.balance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpBalanceClient implements BalanceClient {
	private final RestClient restClient;

	public HttpBalanceClient(
			RestClient.Builder restClientBuilder,
			@Value("${bank-flow.balance.base-url}") String balanceBaseUrl
	) {
		this.restClient = restClientBuilder
				.baseUrl(balanceBaseUrl)
				.build();
	}

	@Override
	public BalanceHoldResponse createHold(CreateBalanceHoldRequest request) {
		return restClient.post()
				.uri("/holds")
				.body(request)
				.retrieve()
				.body(BalanceHoldResponse.class);
	}

	@Override
	public void captureHold(String holdId) {
		restClient.post()
				.uri("/holds/{hold_id}/capture", holdId)
				.retrieve()
				.toBodilessEntity();
	}

	@Override
	public void releaseHold(String holdId) {
		restClient.post()
				.uri("/holds/{hold_id}/release", holdId)
				.retrieve()
				.toBodilessEntity();
	}
}

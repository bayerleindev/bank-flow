package br.com.bankflow.transfer.clients.balance;

import br.com.bankflow.transfer.resilience.HttpResilience;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpBalanceClient implements BalanceClient {
	private final RestClient restClient;
	private final HttpResilience httpResilience;

	public HttpBalanceClient(
			RestClient.Builder restClientBuilder,
			@Value("${bank-flow.balance.base-url}") String balanceBaseUrl,
			HttpResilience httpResilience
	) {
		this.restClient = restClientBuilder
				.baseUrl(balanceBaseUrl)
				.build();
		this.httpResilience = httpResilience;
	}

	@Override
	public BalanceHoldResponse createHold(CreateBalanceHoldRequest request) {
		return httpResilience.execute("balance.createHold", () -> restClient.post()
				.uri("/holds")
				.body(request)
				.retrieve()
				.body(BalanceHoldResponse.class));
	}

	@Override
	public void captureHold(String holdId) {
		httpResilience.execute("balance.captureHold", () -> restClient.post()
				.uri("/holds/{hold_id}/capture", holdId)
				.retrieve()
				.toBodilessEntity());
	}

	@Override
	public void releaseHold(String holdId) {
		httpResilience.execute("balance.releaseHold", () -> restClient.post()
				.uri("/holds/{hold_id}/release", holdId)
				.retrieve()
				.toBodilessEntity());
	}
}

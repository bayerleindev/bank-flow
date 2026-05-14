package br.com.bankflow.accounts.clients.baas;

import br.com.bankflow.accounts.domain.CreateAccountCommand;
import br.com.bankflow.accounts.resilience.HttpResilience;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.format.DateTimeFormatter;

@Component
@ConditionalOnProperty(name = "bank-flow.baas.mode", havingValue = "http")
public class HttpBaasClient implements BaasClient {
	private static final DateTimeFormatter BAAS_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private final RestClient restClient;
	private final HttpResilience httpResilience;

	public HttpBaasClient(
			RestClient.Builder restClientBuilder,
			@Value("${bank-flow.baas.base-url}") String baasBaseUrl,
			HttpResilience httpResilience
	) {
		this.restClient = restClientBuilder.baseUrl(baasBaseUrl).build();
		this.httpResilience = httpResilience;
	}

	@Override
	public BaasAccountResponse createAccount(CreateAccountCommand command) {
		BaasCreateAccountRequest request = new BaasCreateAccountRequest(
				command.fullName(),
				command.documentNumber(),
				command.email(),
				command.motherName(),
				command.socialName(),
				command.phoneNumber(),
				command.birthDate().format(BAAS_DATE_FORMAT),
				command.address(),
				command.politicallyExposed()
		);
		return httpResilience.execute("baas.createAccount", () -> restClient.post()
				.uri("/accounts")
				.header("Idempotency-Key", command.idempotencyKey())
				.body(request)
				.retrieve()
				.body(BaasAccountResponse.class));
	}
}

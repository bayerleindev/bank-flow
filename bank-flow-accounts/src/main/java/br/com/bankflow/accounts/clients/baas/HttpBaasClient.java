package br.com.bankflow.accounts.clients.baas;

import br.com.bankflow.accounts.domain.CreateAccountCommand;
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

	public HttpBaasClient(
			RestClient.Builder restClientBuilder,
			@Value("${bank-flow.baas.base-url}") String baasBaseUrl
	) {
		this.restClient = restClientBuilder.baseUrl(baasBaseUrl).build();
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
		return restClient.post()
				.uri("/accounts")
				.header("Idempotency-Key", command.idempotencyKey())
				.body(request)
				.retrieve()
				.body(BaasAccountResponse.class);
	}
}

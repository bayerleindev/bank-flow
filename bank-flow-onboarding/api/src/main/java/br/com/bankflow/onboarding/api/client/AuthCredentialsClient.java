package br.com.bankflow.onboarding.api.client;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class AuthCredentialsClient {

    private static final String ISSUER_KEY_HEADER = "X-Auth-Issuer-Key";

    private final WebClient webClient;
    private final String issuerKey;
    private final Duration timeout;

    public AuthCredentialsClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.auth.base-url}") String baseUrl,
            @Value("${app.auth.timeout}") Duration timeout,
            @Value("${app.auth.issuer-key}") String issuerKey) {
        this.webClient =
                webClientBuilder
                        .baseUrl(baseUrl)
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .build();
        this.issuerKey = issuerKey;
        this.timeout = timeout;
    }

    public UUID create(UUID onboardingApplicationId, String documentNumber, String password) {
        CreateCredentialsResponse response =
                webClient
                        .post()
                        .uri("/credentials")
                        .header(ISSUER_KEY_HEADER, issuerKey)
                        .bodyValue(
                                new CreateCredentialsRequest(
                                        onboardingApplicationId, documentNumber, password))
                        .retrieve()
                        .bodyToMono(CreateCredentialsResponse.class)
                        .block(timeout);
        if (response == null) {
            throw new IllegalStateException("Auth credentials response is empty");
        }
        return response.id();
    }

    private record CreateCredentialsRequest(
            UUID onboardingApplicationId, String documentNumber, String password) {}

    private record CreateCredentialsResponse(UUID id, String documentNumber) {}
}

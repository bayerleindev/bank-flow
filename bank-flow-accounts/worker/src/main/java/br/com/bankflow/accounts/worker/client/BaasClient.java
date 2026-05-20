package br.com.bankflow.accounts.worker.client;

import br.com.bankflow.accounts.shared.kafka.AccountRequestedEvent;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BaasClient {

    private final WebClient webClient;
    private final Duration timeout;

    public BaasClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.baas.base-url}") String baasBaseUrl,
            @Value("${app.baas.timeout}") Duration timeout) {
        this.webClient = webClientBuilder.baseUrl(baasBaseUrl).build();
        this.timeout = timeout;
    }

    @Retry(name = "baasAccountCreation")
    @CircuitBreaker(name = "baasAccountCreation")
    @Bulkhead(name = "baasAccountCreation", type = Bulkhead.Type.SEMAPHORE)
    public void requestAccountCreation(AccountRequestedEvent event) {
        webClient
                .post()
                .uri("/accounts")
                .bodyValue(
                        new BaasAccountCreationRequest(
                                event.accountId(),
                                event.fullName(),
                                event.documentNumber(),
                                event.email(),
                                event.motherName(),
                                event.socialName(),
                                event.phoneNumber(),
                                event.birthDate().toString(),
                                event.address(),
                                event.politicallyExposed()))
                .retrieve()
                .toBodilessEntity()
                .timeout(timeout)
                .block();
    }
}

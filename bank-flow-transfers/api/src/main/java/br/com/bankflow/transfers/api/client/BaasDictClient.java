package br.com.bankflow.transfers.api.client;

import br.com.bankflow.transfers.shared.domain.PixAccount;
import br.com.bankflow.transfers.shared.domain.PixKeyInfo;
import br.com.bankflow.transfers.shared.domain.PixOwner;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BaasDictClient {

    private final WebClient webClient;
    private final Duration timeout;

    public BaasDictClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.baas.base-url}") String baasBaseUrl,
            @Value("${app.baas.timeout}") Duration timeout) {
        this.webClient = webClientBuilder.baseUrl(baasBaseUrl).build();
        this.timeout = timeout;
    }

    @Retry(name = "baasDictLookup")
    @CircuitBreaker(name = "baasDictLookup")
    @Bulkhead(name = "baasDictLookup", type = Bulkhead.Type.SEMAPHORE)
    public PixKeyInfo findByKey(String key) {
        BaasDictResponse response =
                webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder.path("/dict").queryParam("key", key).build())
                        .retrieve()
                        .bodyToMono(BaasDictResponse.class)
                        .timeout(timeout)
                        .block();

        return response.toDomain();
    }

    private record BaasDictResponse(
            BaasPixAccountResponse account, BaasPixOwnerResponse owner, String endToEndId) {

        private PixKeyInfo toDomain() {
            return new PixKeyInfo(account.toDomain(), owner.toDomain(), endToEndId);
        }
    }

    private record BaasPixAccountResponse(String bank, String account, String branch) {

        private PixAccount toDomain() {
            return new PixAccount(bank, account, branch);
        }
    }

    private record BaasPixOwnerResponse(String name, String maskedDocument) {

        private PixOwner toDomain() {
            return new PixOwner(name, maskedDocument);
        }
    }
}

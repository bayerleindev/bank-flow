package br.com.bankflow.transfers.worker.client;

import br.com.bankflow.transfers.shared.domain.Transfer;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BaasTransferClient {

    private final WebClient webClient;
    private final Duration timeout;

    public BaasTransferClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.baas.base-url}") String baasBaseUrl,
            @Value("${app.baas.timeout}") Duration timeout) {
        this.webClient = webClientBuilder.baseUrl(baasBaseUrl).build();
        this.timeout = timeout;
    }

    @Retry(name = "baasPixPayment")
    @CircuitBreaker(name = "baasPixPayment")
    @Bulkhead(name = "baasPixPayment", type = Bulkhead.Type.SEMAPHORE)
    public void requestPixPayment(Transfer transfer, Instant requestedAt) {
        webClient
                .post()
                .uri("/pix/payments")
                .bodyValue(BaasPixPaymentRequest.from(transfer, requestedAt))
                .retrieve()
                .bodyToMono(BaasPixPaymentResponse.class)
                .timeout(timeout)
                .block();
    }

    private record BaasPixPaymentRequest(
            UUID transferId,
            PartyRequest debitParty,
            PartyRequest creditParty,
            long amountMinor,
            String currency,
            String description,
            Instant requestedAt) {

        private static BaasPixPaymentRequest from(Transfer transfer, Instant requestedAt) {
            return new BaasPixPaymentRequest(
                    transfer.id(),
                    PartyRequest.from(transfer.debitParty()),
                    PartyRequest.from(transfer.creditParty()),
                    transfer.amountMinor(),
                    transfer.currency(),
                    transfer.description(),
                    requestedAt);
        }
    }

    private record PartyRequest(String bank, String account, String branch) {

        private static PartyRequest from(
                br.com.bankflow.transfers.shared.domain.TransferParty party) {
            return new PartyRequest(party.bank(), party.account(), party.branch());
        }
    }

    private record BaasPixPaymentResponse(UUID transferId, String status) {}
}

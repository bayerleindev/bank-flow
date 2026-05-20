package br.com.bankflow.transfers.api.producer;

import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.kafka.TransferRequestedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransferEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String transferRequestedTopic;

    public TransferEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.transfer-requested}") String transferRequestedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.transferRequestedTopic = transferRequestedTopic;
    }

    @Retry(name = "kafkaPublish")
    @CircuitBreaker(name = "kafkaPublish")
    public void publishRequested(Transfer transfer) {
        kafkaTemplate
                .send(transferRequestedTopic, transfer.id().toString(), toEvent(transfer))
                .join();
    }

    private static TransferRequestedEvent toEvent(Transfer transfer) {
        return new TransferRequestedEvent(
                transfer.id(),
                transfer.debitParty(),
                transfer.creditParty(),
                transfer.idempotencyKey(),
                transfer.amountMinor(),
                transfer.description(),
                transfer.currency(),
                transfer.type(),
                transfer.status(),
                transfer.createdAt());
    }
}

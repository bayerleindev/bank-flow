package br.com.bankflow.transfers.worker.producer;

import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.kafka.MovementCreatedEvent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class MovementEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String movementCreatedTopic;

    public MovementEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.movement-created}") String movementCreatedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.movementCreatedTopic = movementCreatedTopic;
    }

    public void publishCreated(Transfer transfer, Instant createdAt) {
        MovementCreatedEvent event =
                new MovementCreatedEvent(
                        movementId(transfer),
                        transfer.id(),
                        transfer.debitAccountId(),
                        transfer.creditAccountId(),
                        transfer.amountMinor(),
                        transfer.currency(),
                        transfer.type().name(),
                        createdAt);

        kafkaTemplate.send(movementCreatedTopic, event.movementId().toString(), event).join();
    }

    private static UUID movementId(Transfer transfer) {
        return UUID.nameUUIDFromBytes(
                ("movement:" + transfer.id()).getBytes(StandardCharsets.UTF_8));
    }
}

package br.com.bankflow.ledger.consumer;

import br.com.bankflow.ledger.kafka.MovementCreatedEvent;
import br.com.bankflow.ledger.service.LedgerMovementService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MovementCreatedConsumer {

    private final LedgerMovementService ledgerMovementService;

    public MovementCreatedConsumer(LedgerMovementService ledgerMovementService) {
        this.ledgerMovementService = ledgerMovementService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.movement-created}",
            containerFactory = "movementCreatedKafkaListenerContainerFactory")
    public void consume(MovementCreatedEvent event) {
        ledgerMovementService.createJournal(event);
    }
}

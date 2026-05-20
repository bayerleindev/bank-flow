package br.com.bankflow.transfers.worker.consumer;

import br.com.bankflow.transfers.shared.kafka.TransferRequestedEvent;
import br.com.bankflow.transfers.worker.service.TransferOrchestratorService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransferRequestedConsumer {

    private final TransferOrchestratorService transferOrchestratorService;

    public TransferRequestedConsumer(TransferOrchestratorService transferOrchestratorService) {
        this.transferOrchestratorService = transferOrchestratorService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.transfer-requested}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(TransferRequestedEvent event) {
        transferOrchestratorService.startProcessing(event);
    }
}

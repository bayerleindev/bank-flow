package br.com.bankflow.transfers.worker.consumer;

import br.com.bankflow.transfers.shared.kafka.AccountValidatedEvent;
import br.com.bankflow.transfers.worker.service.TransferOrchestratorService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AccountValidatedConsumer {

    private final TransferOrchestratorService transferOrchestratorService;

    public AccountValidatedConsumer(TransferOrchestratorService transferOrchestratorService) {
        this.transferOrchestratorService = transferOrchestratorService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.account-validated-event}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "accountValidatedKafkaListenerContainerFactory")
    public void consume(AccountValidatedEvent event) {
        transferOrchestratorService.handleAccountValidated(event);
    }
}

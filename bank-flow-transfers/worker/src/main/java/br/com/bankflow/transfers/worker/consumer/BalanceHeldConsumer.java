package br.com.bankflow.transfers.worker.consumer;

import br.com.bankflow.transfers.shared.kafka.BalanceHeldEvent;
import br.com.bankflow.transfers.worker.service.TransferOrchestratorService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BalanceHeldConsumer {

    private final TransferOrchestratorService transferOrchestratorService;

    public BalanceHeldConsumer(TransferOrchestratorService transferOrchestratorService) {
        this.transferOrchestratorService = transferOrchestratorService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.balance-held-event}",
            containerFactory = "balanceHeldKafkaListenerContainerFactory")
    public void consume(BalanceHeldEvent event) {
        transferOrchestratorService.handleBalanceHeld(event);
    }
}

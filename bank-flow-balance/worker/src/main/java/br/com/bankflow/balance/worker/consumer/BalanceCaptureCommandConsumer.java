package br.com.bankflow.balance.worker.consumer;

import br.com.bankflow.balance.shared.kafka.BalanceCaptureCommand;
import br.com.bankflow.balance.worker.service.BalanceSettlementService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BalanceCaptureCommandConsumer {

    private final BalanceSettlementService balanceSettlementService;

    public BalanceCaptureCommandConsumer(BalanceSettlementService balanceSettlementService) {
        this.balanceSettlementService = balanceSettlementService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.balance-capture-command}",
            containerFactory = "balanceCaptureCommandKafkaListenerContainerFactory")
    public void consume(BalanceCaptureCommand command) {
        balanceSettlementService.capture(command);
    }
}

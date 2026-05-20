package br.com.bankflow.balance.worker.consumer;

import br.com.bankflow.balance.shared.kafka.BalanceReleaseCommand;
import br.com.bankflow.balance.worker.service.BalanceSettlementService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BalanceReleaseCommandConsumer {

    private final BalanceSettlementService balanceSettlementService;

    public BalanceReleaseCommandConsumer(BalanceSettlementService balanceSettlementService) {
        this.balanceSettlementService = balanceSettlementService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.balance-release-command}",
            containerFactory = "balanceReleaseCommandKafkaListenerContainerFactory")
    public void consume(BalanceReleaseCommand command) {
        balanceSettlementService.release(command);
    }
}

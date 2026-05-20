package br.com.bankflow.balance.worker.consumer;

import br.com.bankflow.balance.shared.kafka.BalanceHoldCommand;
import br.com.bankflow.balance.worker.service.BalanceHoldService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BalanceHoldCommandConsumer {

    private final BalanceHoldService balanceHoldService;

    public BalanceHoldCommandConsumer(BalanceHoldService balanceHoldService) {
        this.balanceHoldService = balanceHoldService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.balance-hold-command}",
            containerFactory = "balanceHoldCommandKafkaListenerContainerFactory")
    public void consume(BalanceHoldCommand command) {
        balanceHoldService.hold(command);
    }
}

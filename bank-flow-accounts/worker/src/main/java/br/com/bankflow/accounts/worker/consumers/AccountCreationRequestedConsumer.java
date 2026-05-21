package br.com.bankflow.accounts.worker.consumers;

import br.com.bankflow.accounts.shared.kafka.AccountCreationRequestedEvent;
import br.com.bankflow.accounts.worker.service.AccountCreationRequestedService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AccountCreationRequestedConsumer {

    private final AccountCreationRequestedService accountCreationRequestedService;

    public AccountCreationRequestedConsumer(
            AccountCreationRequestedService accountCreationRequestedService) {
        this.accountCreationRequestedService = accountCreationRequestedService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.account-creation-requested}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "accountCreationRequestedKafkaListenerContainerFactory")
    public void consume(AccountCreationRequestedEvent event) {
        accountCreationRequestedService.process(event);
    }
}

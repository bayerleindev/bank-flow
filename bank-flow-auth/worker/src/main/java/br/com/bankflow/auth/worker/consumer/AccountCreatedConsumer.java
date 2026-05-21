package br.com.bankflow.auth.worker.consumer;

import br.com.bankflow.auth.kafka.AccountCreatedEvent;
import br.com.bankflow.auth.worker.service.AccountLinkService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AccountCreatedConsumer {

    private final AccountLinkService accountLinkService;

    public AccountCreatedConsumer(AccountLinkService accountLinkService) {
        this.accountLinkService = accountLinkService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.account-created}",
            containerFactory = "accountCreatedKafkaListenerContainerFactory")
    public void consume(AccountCreatedEvent event) {
        accountLinkService.link(event);
    }
}

package br.com.bankflow.accounts.worker.consumers;

import br.com.bankflow.accounts.shared.kafka.AccountRequestedEvent;
import br.com.bankflow.accounts.worker.service.AccountRequestedService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AccountRequestedConsumer {

    private final AccountRequestedService accountRequestedService;

    public AccountRequestedConsumer(AccountRequestedService accountRequestedService) {
        this.accountRequestedService = accountRequestedService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.account-requested}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(AccountRequestedEvent event) {
        accountRequestedService.process(event);
    }
}

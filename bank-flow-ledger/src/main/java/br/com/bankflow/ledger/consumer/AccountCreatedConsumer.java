package br.com.bankflow.ledger.consumer;

import br.com.bankflow.ledger.kafka.AccountCreatedEvent;
import br.com.bankflow.ledger.service.LedgerAccountService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AccountCreatedConsumer {

    private final LedgerAccountService ledgerAccountService;

    public AccountCreatedConsumer(LedgerAccountService ledgerAccountService) {
        this.ledgerAccountService = ledgerAccountService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.account-created}",
            containerFactory = "accountCreatedKafkaListenerContainerFactory")
    public void consume(AccountCreatedEvent event) {
        ledgerAccountService.createAccountRepresentation(event);
    }
}

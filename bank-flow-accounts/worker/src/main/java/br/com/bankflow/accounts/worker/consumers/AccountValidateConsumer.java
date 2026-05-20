package br.com.bankflow.accounts.worker.consumers;

import br.com.bankflow.accounts.shared.kafka.AccountValidateCommand;
import br.com.bankflow.accounts.worker.service.AccountValidationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AccountValidateConsumer {

    private final AccountValidationService accountValidationService;

    public AccountValidateConsumer(AccountValidationService accountValidationService) {
        this.accountValidationService = accountValidationService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.account-validate-command}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "accountValidateKafkaListenerContainerFactory")
    public void consume(AccountValidateCommand command) {
        accountValidationService.validate(command);
    }
}

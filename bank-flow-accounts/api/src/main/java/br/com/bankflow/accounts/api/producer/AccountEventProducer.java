package br.com.bankflow.accounts.api.producer;

import br.com.bankflow.accounts.shared.domain.Account;
import br.com.bankflow.accounts.shared.kafka.AccountCreatedEvent;
import br.com.bankflow.accounts.shared.kafka.AccountRequestedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String accountRequestedTopic;
    private final String accountCreatedTopic;

    public AccountEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.account-requested}") String accountRequestedTopic,
            @Value("${app.kafka.topics.account-created}") String accountCreatedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.accountRequestedTopic = accountRequestedTopic;
        this.accountCreatedTopic = accountCreatedTopic;
    }

    @Retry(name = "kafkaPublish")
    @CircuitBreaker(name = "kafkaPublish")
    public void publishRequested(Account account) {
        AccountRequestedEvent event =
                new AccountRequestedEvent(
                        account.id(),
                        account.fullName(),
                        account.documentNumber(),
                        account.email(),
                        account.motherName(),
                        account.socialName(),
                        account.phoneNumber(),
                        account.birthDate(),
                        account.address(),
                        account.politicallyExposed());

        kafkaTemplate.send(accountRequestedTopic, account.id().toString(), event).join();
    }

    @Retry(name = "kafkaPublish")
    @CircuitBreaker(name = "kafkaPublish")
    public void publishCreated(Account account) {
        AccountCreatedEvent event =
                new AccountCreatedEvent(
                        account.id(),
                        account.documentNumber(),
                        account.branchNumber(),
                        account.accountNumber(),
                        account.accountDigit());

        kafkaTemplate.send(accountCreatedTopic, account.id().toString(), event).join();
    }
}

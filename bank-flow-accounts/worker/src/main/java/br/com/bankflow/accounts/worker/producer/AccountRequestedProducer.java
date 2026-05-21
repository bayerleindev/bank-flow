package br.com.bankflow.accounts.worker.producer;

import br.com.bankflow.accounts.shared.domain.Account;
import br.com.bankflow.accounts.shared.kafka.AccountRequestedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountRequestedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String accountRequestedTopic;

    public AccountRequestedProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.account-requested}") String accountRequestedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.accountRequestedTopic = accountRequestedTopic;
    }

    @Retry(name = "kafkaPublish")
    @CircuitBreaker(name = "kafkaPublish")
    public void publish(Account account) {
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
}

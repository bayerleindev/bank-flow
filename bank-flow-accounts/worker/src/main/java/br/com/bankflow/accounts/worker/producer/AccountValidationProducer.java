package br.com.bankflow.accounts.worker.producer;

import br.com.bankflow.accounts.shared.kafka.AccountValidatedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountValidationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String accountValidatedEventTopic;

    public AccountValidationProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.account-validated-event}")
                    String accountValidatedEventTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.accountValidatedEventTopic = accountValidatedEventTopic;
    }

    @Retry(name = "accountValidationPublish")
    @CircuitBreaker(name = "accountValidationPublish")
    public void publish(AccountValidatedEvent event) {
        kafkaTemplate.send(accountValidatedEventTopic, event.transferId().toString(), event).join();
    }
}

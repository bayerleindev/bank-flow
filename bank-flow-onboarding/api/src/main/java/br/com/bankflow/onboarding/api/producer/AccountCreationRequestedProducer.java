package br.com.bankflow.onboarding.api.producer;

import br.com.bankflow.onboarding.api.service.OnboardingApplication;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountCreationRequestedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String accountCreationRequestedTopic;

    public AccountCreationRequestedProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.account-creation-requested}")
                    String accountCreationRequestedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.accountCreationRequestedTopic = accountCreationRequestedTopic;
    }

    @Retry(name = "kafkaPublish")
    @CircuitBreaker(name = "kafkaPublish")
    public void publish(OnboardingApplication application) {
        AccountCreationRequestedEvent event =
                new AccountCreationRequestedEvent(
                        application.id(),
                        application.credentialsId(),
                        application.fullName(),
                        application.documentNumber(),
                        application.email(),
                        application.motherName(),
                        application.socialName(),
                        application.phoneNumber(),
                        application.birthDate(),
                        application.address(),
                        application.politicallyExposed(),
                        application.approvedAt());
        kafkaTemplate
                .send(accountCreationRequestedTopic, application.id().toString(), event)
                .join();
    }
}

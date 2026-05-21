package br.com.bankflow.onboarding.api.producer;

import br.com.bankflow.onboarding.api.service.OnboardingApplication;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OnboardingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String onboardingApprovedTopic;

    public OnboardingEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.onboarding-approved}") String onboardingApprovedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.onboardingApprovedTopic = onboardingApprovedTopic;
    }

    @Retry(name = "kafkaPublish")
    @CircuitBreaker(name = "kafkaPublish")
    public void publishApproved(OnboardingApplication application) {
        OnboardingApprovedEvent event =
                new OnboardingApprovedEvent(
                        application.id(),
                        application.fullName(),
                        application.documentNumber(),
                        application.email(),
                        application.motherName(),
                        application.socialName(),
                        application.phoneNumber(),
                        application.birthDate(),
                        application.address(),
                        application.politicallyExposed(),
                        application.credentialsId(),
                        application.approvedAt());
        kafkaTemplate.send(onboardingApprovedTopic, application.id().toString(), event).join();
    }
}

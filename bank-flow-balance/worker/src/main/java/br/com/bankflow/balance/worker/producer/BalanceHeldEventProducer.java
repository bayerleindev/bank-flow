package br.com.bankflow.balance.worker.producer;

import br.com.bankflow.balance.shared.kafka.BalanceHeldEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BalanceHeldEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String balanceHeldEventTopic;

    public BalanceHeldEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.balance-held-event}") String balanceHeldEventTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.balanceHeldEventTopic = balanceHeldEventTopic;
    }

    public void publish(BalanceHeldEvent event) {
        kafkaTemplate.send(balanceHeldEventTopic, event.transferId().toString(), event).join();
    }
}

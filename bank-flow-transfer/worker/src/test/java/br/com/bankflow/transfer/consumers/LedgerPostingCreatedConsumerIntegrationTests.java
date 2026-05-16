package br.com.bankflow.transfer.consumers;

import br.com.bankflow.transfer.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.transfer.services.TransferOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "bank-flow.kafka.topics.ledger-posting-created=ledger-posting-created-it",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.datasource.url=jdbc:h2:mem:worker_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@EmbeddedKafka(partitions = 1, topics = {"ledger-posting-created-it"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class LedgerPostingCreatedConsumerIntegrationTests {

    private static final String TOPIC = "ledger-posting-created-it";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransferOrchestrationService transferOrchestrationService;

    @Test
    void consumesLedgerPostingEvent() throws Exception {
        String externalId = UUID.randomUUID().toString();
        LedgerPostingCreatedEvent event = new LedgerPostingCreatedEvent(
                123L, externalId, "TRANSFER", "POSTED"
        );

        String payload = objectMapper.writeValueAsString(event);
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, externalId, payload);
        record.headers().add("transfer_id", UUID.randomUUID().toString().getBytes());

        kafkaTemplate.send(record).get();

        verify(transferOrchestrationService, timeout(10000)).completeAfterLedgerPosting(any());
    }
}

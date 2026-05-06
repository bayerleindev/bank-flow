package br.com.bankflow.ledger.consumers;

import br.com.bankflow.ledger.domain.LedgerReversalRequestedEvent;
import br.com.bankflow.ledger.services.LedgerReversalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class LedgerReversalConsumer {
	private static final Logger log = LoggerFactory.getLogger(LedgerReversalConsumer.class);

	private final ObjectMapper objectMapper;
	private final LedgerReversalService ledgerReversalService;

	public LedgerReversalConsumer(ObjectMapper objectMapper, LedgerReversalService ledgerReversalService) {
		this.objectMapper = objectMapper;
		this.ledgerReversalService = ledgerReversalService;
	}

	@KafkaListener(
			topics = "${bank-flow.kafka.topics.ledger-reversals}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "ledgerReversalKafkaListenerContainerFactory",
			autoStartup = "${spring.kafka.listener.auto-startup:true}"
	)
	public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
		LedgerReversalRequestedEvent event = objectMapper.readValue(record.value(), LedgerReversalRequestedEvent.class);
		validatePartitionKey(record.key(), event);

		ledgerReversalService.reverse(event);
		acknowledgment.acknowledge();

		log.debug(
				"ledger-reversals consumed topic={} partition={} offset={} reversalId={} originalExternalId={}",
				record.topic(),
				record.partition(),
				record.offset(),
				event.reversalId(),
				event.originalExternalId()
		);
	}

	private void validatePartitionKey(String key, LedgerReversalRequestedEvent event) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Kafka key original_external_id is required");
		}
		if (!key.equals(event.originalExternalId())) {
			throw new IllegalArgumentException("Kafka key original_external_id must match event original_external_id");
		}
	}
}

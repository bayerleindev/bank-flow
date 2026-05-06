package br.com.bankflow.ledger.consumers;

import br.com.bankflow.ledger.domain.TransferPostedEvent;
import br.com.bankflow.ledger.services.LedgerMovementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LedgerMovementConsumer {
	private static final Logger log = LoggerFactory.getLogger(LedgerMovementConsumer.class);

	private final ObjectMapper objectMapper;
	private final LedgerMovementService ledgerMovementService;

	public LedgerMovementConsumer(ObjectMapper objectMapper, LedgerMovementService ledgerMovementService) {
		this.objectMapper = objectMapper;
		this.ledgerMovementService = ledgerMovementService;
	}

	@KafkaListener(
			topics = "${bank-flow.kafka.topics.ledger-movements}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "ledgerMovementKafkaListenerContainerFactory",
			autoStartup = "${spring.kafka.listener.auto-startup:true}"
	)
	public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
		TransferPostedEvent event = objectMapper.readValue(record.value(), TransferPostedEvent.class);
		validatePartitionKey(record.key(), event);

		ledgerMovementService.postTransfer(event);
		acknowledgment.acknowledge();

		log.debug(
				"ledger-movements consumed topic={} partition={} offset={} transferId={}",
				record.topic(),
				record.partition(),
				record.offset(),
				event.transferId()
		);
	}

	private void validatePartitionKey(String key, TransferPostedEvent event) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Kafka key source_owner_id is required");
		}

		UUID sourceOwnerIdKey = UUID.fromString(key);
		if (!sourceOwnerIdKey.equals(event.sourceOwnerId())) {
			throw new IllegalArgumentException("Kafka key source_owner_id must match event source_owner_id");
		}
	}
}

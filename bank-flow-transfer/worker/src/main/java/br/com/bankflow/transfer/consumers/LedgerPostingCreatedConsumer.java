package br.com.bankflow.transfer.consumers;

import br.com.bankflow.transfer.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.transfer.services.TransferOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class LedgerPostingCreatedConsumer {
	private final ObjectMapper objectMapper;
	private final TransferOrchestrationService transferOrchestrationService;

	public LedgerPostingCreatedConsumer(
			ObjectMapper objectMapper,
			TransferOrchestrationService transferOrchestrationService
	) {
		this.objectMapper = objectMapper;
		this.transferOrchestrationService = transferOrchestrationService;
	}

	@KafkaListener(
			topics = "${bank-flow.kafka.topics.ledger-posting-created}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "ledgerPostingCreatedKafkaListenerContainerFactory",
			autoStartup = "${spring.kafka.listener.auto-startup:true}"
	)
	public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
		LedgerPostingCreatedEvent event = objectMapper.readValue(record.value(), LedgerPostingCreatedEvent.class);
		event.validate();
		if (!event.isTransferPosting()) {
			acknowledgment.acknowledge();
			return;
		}
		validatePartitionKey(record.key(), event);
		transferOrchestrationService.completeAfterLedgerPosting(event);
		acknowledgment.acknowledge();
	}

	private void validatePartitionKey(String key, LedgerPostingCreatedEvent event) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Kafka key external_id is required");
		}
		if (!key.equals(event.externalId())) {
			throw new IllegalArgumentException("Kafka key external_id must match event external_id");
		}
	}
}

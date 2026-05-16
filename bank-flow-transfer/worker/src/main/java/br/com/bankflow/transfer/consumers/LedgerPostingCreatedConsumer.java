package br.com.bankflow.transfer.consumers;

import br.com.bankflow.transfer.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.transfer.observability.TransferBusinessMetrics;
import br.com.bankflow.transfer.observability.TransferTracing;
import br.com.bankflow.transfer.services.TransferOrchestrationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class LedgerPostingCreatedConsumer {
	private final ObjectMapper objectMapper;
	private final TransferOrchestrationService transferOrchestrationService;
	private final TransferBusinessMetrics transferBusinessMetrics;
    private final TransferTracing transferTracing;

	public LedgerPostingCreatedConsumer(
            ObjectMapper objectMapper,
            TransferOrchestrationService transferOrchestrationService,
            TransferBusinessMetrics transferBusinessMetrics,
            TransferTracing transferTracing
    ) {
		this.objectMapper = objectMapper;
		this.transferOrchestrationService = transferOrchestrationService;
		this.transferBusinessMetrics = transferBusinessMetrics;
        this.transferTracing = transferTracing;
	}

	@KafkaListener(
			topics = "${bank-flow.kafka.topics.ledger-posting-created}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "ledgerPostingCreatedKafkaListenerContainerFactory",
			autoStartup = "${spring.kafka.listener.auto-startup:true}"
	)
	public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        UUID transferId = extractTransferId(record);
        String label = transferId != null ? "with_transfer_id" : "missing_transfer_id";

        transferTracing.withTransferId(transferId, () -> {
			transferBusinessMetrics.recordTransferIdContext("ledger_posting_consume", label);
            try {
                LedgerPostingCreatedEvent event = objectMapper.readValue(record.value(), LedgerPostingCreatedEvent.class);
                event.validate();
                if (!event.isTransferPosting()) {
                    acknowledgment.acknowledge();
                    return;
                }
                validatePartitionKey(record.key(), event);
                transferOrchestrationService.completeAfterLedgerPosting(event);
                acknowledgment.acknowledge();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
		});
	}

	private UUID extractTransferId(ConsumerRecord<String, String> record) {
		var header = record.headers().lastHeader("transfer_id");
		if (header == null || header.value() == null || header.value().length == 0) {
			return null;
		}
		String transferIdStr = new String(header.value(), StandardCharsets.UTF_8);
        try {
            return UUID.fromString(transferIdStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
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

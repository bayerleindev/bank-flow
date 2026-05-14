package br.com.bankflow.transfer.consumers;

import br.com.bankflow.transfer.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.transfer.observability.KafkaConsumerTracing;
import br.com.bankflow.transfer.observability.TransferBusinessMetrics;
import br.com.bankflow.transfer.services.TransferOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class LedgerPostingCreatedConsumer {
	private final ObjectMapper objectMapper;
	private final TransferOrchestrationService transferOrchestrationService;
	private final TransferBusinessMetrics transferBusinessMetrics;
	private final KafkaConsumerTracing kafkaConsumerTracing;

	public LedgerPostingCreatedConsumer(
			ObjectMapper objectMapper,
			TransferOrchestrationService transferOrchestrationService,
			TransferBusinessMetrics transferBusinessMetrics,
			KafkaConsumerTracing kafkaConsumerTracing
	) {
		this.objectMapper = objectMapper;
		this.transferOrchestrationService = transferOrchestrationService;
		this.transferBusinessMetrics = transferBusinessMetrics;
		this.kafkaConsumerTracing = kafkaConsumerTracing;
	}

	@KafkaListener(
			topics = "${bank-flow.kafka.topics.ledger-posting-created}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "ledgerPostingCreatedKafkaListenerContainerFactory",
			autoStartup = "${spring.kafka.listener.auto-startup:true}"
	)
	public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
		kafkaConsumerTracing.consume(record, "ledger.posting_created", () -> {
			transferBusinessMetrics.recordTraceContext("ledger_posting_consume", traceContextLabel(record));
			LedgerPostingCreatedEvent event = objectMapper.readValue(record.value(), LedgerPostingCreatedEvent.class);
			event.validate();
			if (!event.isTransferPosting()) {
				acknowledgment.acknowledge();
				return;
			}
			validatePartitionKey(record.key(), event);
			transferOrchestrationService.completeAfterLedgerPosting(event);
			acknowledgment.acknowledge();
		});
	}

	private String traceContextLabel(ConsumerRecord<String, String> record) {
		var header = record.headers().lastHeader("traceparent");
		if (header == null || header.value() == null || header.value().length == 0) {
			return "missing_trace";
		}
		String traceparent = new String(header.value(), StandardCharsets.UTF_8);
		return traceparent.isBlank() ? "missing_trace" : "with_trace";
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

package br.com.bankflow.balance.consumers;

import br.com.bankflow.balance.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.balance.observability.BalanceMetrics;
import br.com.bankflow.balance.observability.KafkaConsumerTracing;
import br.com.bankflow.balance.services.LedgerPostingProjectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class LedgerPostingCreatedConsumer {
	private static final Logger log = LoggerFactory.getLogger(LedgerPostingCreatedConsumer.class);

	private final ObjectMapper objectMapper;
	private final LedgerPostingProjectionService ledgerPostingProjectionService;
	private final BalanceMetrics balanceMetrics;
	private final KafkaConsumerTracing kafkaConsumerTracing;

	public LedgerPostingCreatedConsumer(
			ObjectMapper objectMapper,
			LedgerPostingProjectionService ledgerPostingProjectionService,
			BalanceMetrics balanceMetrics,
			KafkaConsumerTracing kafkaConsumerTracing
	) {
		this.objectMapper = objectMapper;
		this.ledgerPostingProjectionService = ledgerPostingProjectionService;
		this.balanceMetrics = balanceMetrics;
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
			balanceMetrics.recordKafkaMessageReceived(record.topic());
			balanceMetrics.recordKafkaTraceContext(record.topic(), traceContextLabel(record));
			try {
				LedgerPostingCreatedEvent event = objectMapper.readValue(record.value(), LedgerPostingCreatedEvent.class);
				validatePartitionKey(record.key(), event);

				ledgerPostingProjectionService.project(event);
				acknowledgment.acknowledge();

				log.debug(
						"ledger-posting-created consumed topic={} partition={} offset={} entryId={} externalId={}",
						record.topic(),
						record.partition(),
						record.offset(),
						event.entryId(),
						event.externalId()
				);
			} catch (Exception exception) {
				balanceMetrics.recordKafkaMessageFailed(record.topic(), exception);
				throw exception;
			}
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

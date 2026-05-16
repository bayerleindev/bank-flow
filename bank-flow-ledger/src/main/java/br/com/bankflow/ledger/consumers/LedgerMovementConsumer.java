package br.com.bankflow.ledger.consumers;

import br.com.bankflow.ledger.domain.TransferPostedEvent;
import br.com.bankflow.ledger.observability.KafkaConsumerTracing;
import br.com.bankflow.ledger.observability.LedgerBusinessMetrics;
import br.com.bankflow.ledger.services.LedgerMovementService;
import br.com.bankflow.ledger.observability.TransferTracing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class LedgerMovementConsumer {
	private static final Logger log = LoggerFactory.getLogger(LedgerMovementConsumer.class);

	private final ObjectMapper objectMapper;
	private final LedgerMovementService ledgerMovementService;
	private final LedgerBusinessMetrics ledgerBusinessMetrics;
    private final TransferTracing transferTracing;

	public LedgerMovementConsumer(
            ObjectMapper objectMapper,
            LedgerMovementService ledgerMovementService,
            LedgerBusinessMetrics ledgerBusinessMetrics,
            KafkaConsumerTracing kafkaConsumerTracing, TransferTracing transferTracing
    ) {
		this.objectMapper = objectMapper;
		this.ledgerMovementService = ledgerMovementService;
		this.ledgerBusinessMetrics = ledgerBusinessMetrics;
        this.transferTracing = transferTracing;
	}

	@KafkaListener(
			topics = "${bank-flow.kafka.topics.ledger-movements}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "ledgerMovementKafkaListenerContainerFactory",
			autoStartup = "${spring.kafka.listener.auto-startup:true}"
	)
	public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
        transferTracing.withTransferId(UUID.fromString(transferIdLabel(record)), () -> {
			ledgerBusinessMetrics.recordKafkaTransferIdContext(record.topic(), transferIdLabel(record));
            try {
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

            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

		});
	}

	private void validatePartitionKey(String key, TransferPostedEvent event) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Kafka key source_digital_account_id is required");
		}

		UUID sourceDigitalAccountIdKey = UUID.fromString(key);
		if (!sourceDigitalAccountIdKey.equals(event.sourceDigitalAccountId())) {
			throw new IllegalArgumentException("Kafka key source_digital_account_id must match event source_digital_account_id");
		}
	}

	private String transferIdLabel(ConsumerRecord<String, String> record) {
		var header = record.headers().lastHeader("transfer_id");
		if (header == null || header.value() == null || header.value().length == 0) {
			return "missing_transfer_id";
		}
		String transferId = new String(header.value(), StandardCharsets.UTF_8);
		return transferId.isBlank() ? "missing_transfer_id" : "with_transfer_id";
	}
}

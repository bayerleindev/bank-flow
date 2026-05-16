package br.com.bankflow.balance.consumers;

import br.com.bankflow.balance.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.balance.observability.BalanceMetrics;
import br.com.bankflow.balance.observability.TransferTracing;
import br.com.bankflow.balance.services.LedgerPostingProjectionService;
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
public class LedgerPostingCreatedConsumer {
	private static final Logger log = LoggerFactory.getLogger(LedgerPostingCreatedConsumer.class);

	private final ObjectMapper objectMapper;
	private final LedgerPostingProjectionService ledgerPostingProjectionService;
	private final BalanceMetrics balanceMetrics;
    private final TransferTracing transferTracing;

	public LedgerPostingCreatedConsumer(
            ObjectMapper objectMapper,
            LedgerPostingProjectionService ledgerPostingProjectionService,
            BalanceMetrics balanceMetrics,
            TransferTracing transferTracing
    ) {
		this.objectMapper = objectMapper;
		this.ledgerPostingProjectionService = ledgerPostingProjectionService;
		this.balanceMetrics = balanceMetrics;
        this.transferTracing = transferTracing;
	}

	@KafkaListener(
			topics = "${bank-flow.kafka.topics.ledger-posting-created}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "ledgerPostingCreatedKafkaListenerContainerFactory",
			autoStartup = "${spring.kafka.listener.auto-startup:true}"
	)
	public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
        transferTracing.withTransferId(UUID.fromString(transferIdLabel(record)), () -> {
			balanceMetrics.recordKafkaMessageReceived(record.topic());
			balanceMetrics.recordKafkaTransferIdContext(record.topic(), transferIdLabel(record));
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
			}
		});
	}

	private String transferIdLabel(ConsumerRecord<String, String> record) {
		var header = record.headers().lastHeader("transfer_id");
		if (header == null || header.value() == null || header.value().length == 0) {
			return "missing_transfer_id";
		}
		String transferId = new String(header.value(), StandardCharsets.UTF_8);
		return transferId.isBlank() ? "missing_transfer_id" : "with_transfer_id";
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

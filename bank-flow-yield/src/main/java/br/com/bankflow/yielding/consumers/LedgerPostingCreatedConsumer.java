package br.com.bankflow.yielding.consumers;

import br.com.bankflow.yielding.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.yielding.repositories.YieldRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LedgerPostingCreatedConsumer {
	private static final Logger log = LoggerFactory.getLogger(LedgerPostingCreatedConsumer.class);
	private static final String YIELD_ENTRY_TYPE = "YIELD_CDI";

	private final ObjectMapper objectMapper;
	private final YieldRepository yieldRepository;

	public LedgerPostingCreatedConsumer(ObjectMapper objectMapper, YieldRepository yieldRepository) {
		this.objectMapper = objectMapper;
		this.yieldRepository = yieldRepository;
	}

	@KafkaListener(
			topics = "${bank-flow.kafka.topics.ledger-posting-created}",
			groupId = "${spring.kafka.consumer.group-id}",
			autoStartup = "${spring.kafka.listener.auto-startup:true}"
	)
	public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
		LedgerPostingCreatedEvent event = objectMapper.readValue(record.value(), LedgerPostingCreatedEvent.class);
		if (!YIELD_ENTRY_TYPE.equals(event.entryType())) {
			acknowledgment.acknowledge();
			return;
		}
		event.validate();
		UUID accrualId = UUID.fromString(event.externalId());
		boolean updated = yieldRepository.markPosted(accrualId);
		if (!updated) {
			log.warn("yield accrual not found for posted ledger event accrualId={}", accrualId);
		}
		acknowledgment.acknowledge();
		log.info("yield accrual marked posted accrualId={} entryId={}", accrualId, event.entryId());
	}
}

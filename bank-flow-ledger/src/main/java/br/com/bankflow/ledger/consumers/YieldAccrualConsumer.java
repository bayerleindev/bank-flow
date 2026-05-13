package br.com.bankflow.ledger.consumers;

import br.com.bankflow.ledger.domain.YieldAccrualEvent;
import br.com.bankflow.ledger.services.YieldAccrualService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class YieldAccrualConsumer {
	private static final Logger log = LoggerFactory.getLogger(YieldAccrualConsumer.class);

	private final ObjectMapper objectMapper;
	private final YieldAccrualService yieldAccrualService;

	public YieldAccrualConsumer(ObjectMapper objectMapper, YieldAccrualService yieldAccrualService) {
		this.objectMapper = objectMapper;
		this.yieldAccrualService = yieldAccrualService;
	}

	@KafkaListener(
			topics = "${bank-flow.kafka.topics.yield-accruals}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "yieldAccrualKafkaListenerContainerFactory",
			autoStartup = "${spring.kafka.listener.auto-startup:true}"
	)
	public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
		YieldAccrualEvent event = objectMapper.readValue(record.value(), YieldAccrualEvent.class);
		validatePartitionKey(record.key(), event);
		yieldAccrualService.postYieldAccrual(event);
		acknowledgment.acknowledge();
		log.debug(
				"yield-accruals consumed topic={} partition={} offset={} accrualId={}",
				record.topic(),
				record.partition(),
				record.offset(),
				event.accrualId()
		);
	}

	private void validatePartitionKey(String key, YieldAccrualEvent event) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Kafka key digital_account_id is required");
		}
		UUID digitalAccountIdKey = UUID.fromString(key);
		if (!digitalAccountIdKey.equals(event.digitalAccountId())) {
			throw new IllegalArgumentException("Kafka key digital_account_id must match event digital_account_id");
		}
	}
}

package br.com.bankflow.ledger.consumers;

import br.com.bankflow.ledger.domain.AccountCreatedEvent;
import br.com.bankflow.ledger.domain.AccountCreatedEventHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AccountCreatedConsumer {
	private static final Logger log = LoggerFactory.getLogger(AccountCreatedConsumer.class);

	private final ObjectMapper objectMapper;
	private final AccountCreatedEventHandler handler;

	public AccountCreatedConsumer(ObjectMapper objectMapper, AccountCreatedEventHandler handler) {
		this.objectMapper = objectMapper;
		this.handler = handler;
	}

	@KafkaListener(
			topics = "${bank-flow.kafka.topics.account-created}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "accountCreatedKafkaListenerContainerFactory",
			autoStartup = "${spring.kafka.listener.auto-startup:true}"
	)
	public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
		AccountCreatedEvent event = objectMapper.readValue(record.value(), AccountCreatedEvent.class);
		validatePartitionKey(record.key(), event);

		handler.handle(event);
		acknowledgment.acknowledge();

		log.debug(
				"account-created consumed topic={} partition={} offset={} digitalAccountId={}",
				record.topic(),
				record.partition(),
				record.offset(),
				event.digitalAccountId()
		);
	}

	private void validatePartitionKey(String key, AccountCreatedEvent event) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Kafka key digital_account_id is required");
		}

		UUID digitalAccountIdKey = UUID.fromString(key);
		if (!digitalAccountIdKey.equals(event.digitalAccountId())) {
			throw new IllegalArgumentException("Kafka key digital_account_id must match event digital_account_id");
		}
	}
}

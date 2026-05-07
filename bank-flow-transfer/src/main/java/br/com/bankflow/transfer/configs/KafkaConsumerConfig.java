package br.com.bankflow.transfer.configs;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {
	@Bean
	ConcurrentKafkaListenerContainerFactory<String, String> ledgerPostingCreatedKafkaListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory,
			CommonErrorHandler kafkaErrorHandler
	) {
		ConcurrentKafkaListenerContainerFactory<String, String> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(consumerFactory);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
		factory.setCommonErrorHandler(kafkaErrorHandler);
		return factory;
	}

	@Bean
	CommonErrorHandler kafkaErrorHandler(
			KafkaOperations<Object, Object> kafkaOperations,
			@Value("${bank-flow.kafka.retry.interval-ms}") long intervalMs,
			@Value("${bank-flow.kafka.retry.max-attempts}") long maxAttempts
	) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
				kafkaOperations,
				(record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition())
		);
		DefaultErrorHandler errorHandler = new DefaultErrorHandler(
				recoverer,
				new FixedBackOff(intervalMs, Math.max(0, maxAttempts - 1))
		);
		errorHandler.setCommitRecovered(true);
		errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, JsonProcessingException.class);
		return errorHandler;
	}
}

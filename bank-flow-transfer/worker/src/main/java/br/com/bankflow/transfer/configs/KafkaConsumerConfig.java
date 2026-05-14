package br.com.bankflow.transfer.configs;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {
	@Bean
	ConcurrentKafkaListenerContainerFactory<String, String> ledgerPostingCreatedKafkaListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory,
			CommonErrorHandler kafkaErrorHandler,
			@Value("${spring.kafka.listener.concurrency:2}") int concurrency
	) {
		ConcurrentKafkaListenerContainerFactory<String, String> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(consumerFactory);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
		factory.getContainerProperties().setObservationEnabled(true);
		factory.setCommonErrorHandler(kafkaErrorHandler);
		factory.setConcurrency(Math.max(1, concurrency));
		return factory;
	}

	@Bean
	CommonErrorHandler kafkaErrorHandler(
			KafkaOperations<Object, Object> kafkaOperations,
			MeterRegistry meterRegistry,
			@Value("${spring.application.name:bank-flow-transfer-worker}") String serviceName,
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
		errorHandler.setRetryListeners(dlqMetrics(meterRegistry, serviceName));
		return errorHandler;
	}

	private RetryListener dlqMetrics(MeterRegistry meterRegistry, String serviceName) {
		return new RetryListener() {
			@Override
			public void failedDelivery(
					org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
					Exception exception,
					int deliveryAttempt
			) {
				// Metrics are recorded only when the record is actually recovered to DLT.
			}

			@Override
			public void recovered(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record, Exception exception) {
				Counter.builder("kafka_dlq_records")
						.description("Kafka records published to dead-letter topics")
						.tag("service", serviceName)
						.tag("source_topic", record.topic())
						.tag("dlq_topic", record.topic() + ".DLT")
						.tag("exception", exception.getClass().getSimpleName())
						.register(meterRegistry)
						.increment();
			}
		};
	}
}

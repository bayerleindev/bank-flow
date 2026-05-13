package br.com.bankflow.ledger.configs;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {
	private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

	@Bean
	ConcurrentKafkaListenerContainerFactory<String, String> accountCreatedKafkaListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory,
			CommonErrorHandler kafkaErrorHandler
	) {
		return kafkaListenerContainerFactory(consumerFactory, kafkaErrorHandler);
	}

	@Bean
	ConcurrentKafkaListenerContainerFactory<String, String> ledgerMovementKafkaListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory,
			CommonErrorHandler kafkaErrorHandler
	) {
		return kafkaListenerContainerFactory(consumerFactory, kafkaErrorHandler);
	}

	@Bean
	ConcurrentKafkaListenerContainerFactory<String, String> ledgerReversalKafkaListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory,
			CommonErrorHandler kafkaErrorHandler
	) {
		return kafkaListenerContainerFactory(consumerFactory, kafkaErrorHandler);
	}

	@Bean
	ConcurrentKafkaListenerContainerFactory<String, String> yieldAccrualKafkaListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory,
			CommonErrorHandler kafkaErrorHandler
	) {
		return kafkaListenerContainerFactory(consumerFactory, kafkaErrorHandler);
	}

	private ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory,
			CommonErrorHandler kafkaErrorHandler
	) {
		ConcurrentKafkaListenerContainerFactory<String, String> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(consumerFactory);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
		factory.getContainerProperties().setObservationEnabled(true);
		factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(3);
		return factory;
	}

	@Bean
	CommonErrorHandler kafkaErrorHandler(
			KafkaOperations<Object, Object> kafkaOperations,
			MeterRegistry meterRegistry,
			@Value("${spring.application.name:bank-flow-ledger}") String serviceName,
			@Value("${bank-flow.kafka.retry.interval-ms}") long intervalMs,
			@Value("${bank-flow.kafka.retry.max-attempts}") long maxAttempts
	) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
				kafkaOperations,
				(record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition())
		);
		long maxRetries = Math.max(0, maxAttempts - 1);
		DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(intervalMs, maxRetries));
		errorHandler.setCommitRecovered(true);
		errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, JsonProcessingException.class);
		errorHandler.setRetryListeners(retryLogger(meterRegistry, serviceName));
		return errorHandler;
	}

	private RetryListener retryLogger(MeterRegistry meterRegistry, String serviceName) {
		return new RetryListener() {
			@Override
			public void failedDelivery(
					org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
					Exception exception,
					int deliveryAttempt
			) {
				log.warn(
						"kafka record processing failed topic={} partition={} offset={} attempt={} error={}",
						record.topic(),
						record.partition(),
						record.offset(),
						deliveryAttempt,
						exception.getMessage(),
						exception
				);
			}

			@Override
			public void recovered(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record, Exception exception) {
				recordDlq(meterRegistry, serviceName, record.topic(), exception);
				log.warn(
						"kafka record sent to DLT topic={} partition={} offset={} error={}",
						record.topic(),
						record.partition(),
						record.offset(),
						exception.getMessage()
				);
			}

			@Override
			public void recoveryFailed(
					org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
					Exception original,
					Exception failure
			) {
				log.error(
						"kafka record DLT publishing failed topic={} partition={} offset={} originalError={} recoveryError={}",
						record.topic(),
						record.partition(),
						record.offset(),
						original.getMessage(),
						failure.getMessage(),
						failure
				);
			}
		};
	}

	private void recordDlq(MeterRegistry meterRegistry, String serviceName, String sourceTopic, Exception exception) {
		Counter.builder("kafka_dlq_records")
				.description("Kafka records published to dead-letter topics")
				.tag("service", serviceName)
				.tag("source_topic", sourceTopic)
				.tag("dlq_topic", sourceTopic + ".DLT")
				.tag("exception", exception.getClass().getSimpleName())
				.register(meterRegistry)
				.increment();
	}
}

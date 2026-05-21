package br.com.bankflow.auth.worker.config;

import br.com.bankflow.auth.kafka.AccountCreatedEvent;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, AccountCreatedEvent>
            accountCreatedKafkaListenerContainerFactory(
                    @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                    @Value("${spring.kafka.consumer.group-id}") String groupId,
                    @Value("${spring.kafka.consumer.auto-offset-reset}") String autoOffsetReset,
                    DefaultErrorHandler authWorkerErrorHandler,
                    @Value("${app.kafka.consumer.account-created.concurrency}") int concurrency,
                    @Value("${app.kafka.consumer.max-poll-records}") String maxPollRecords) {
        ConcurrentKafkaListenerContainerFactory<String, AccountCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(
                consumerFactory(bootstrapServers, groupId, autoOffsetReset, maxPollRecords));
        factory.setConcurrency(concurrency);
        factory.setCommonErrorHandler(authWorkerErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setContainerCustomizer(this::customizeContainer);
        return factory;
    }

    @Bean
    DefaultErrorHandler authWorkerErrorHandler(
            @Value("${app.kafka.consumer.retry.max-attempts}") int maxAttempts,
            @Value("${app.kafka.consumer.retry.initial-interval-ms}") long initialIntervalMs,
            @Value("${app.kafka.consumer.retry.multiplier}") double multiplier,
            @Value("${app.kafka.consumer.retry.max-interval-ms}") long maxIntervalMs) {
        ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
        backOff.setMaxInterval(maxIntervalMs);
        backOff.setMaxElapsedTime(maxIntervalMs * Math.max(1, maxAttempts));
        return new DefaultErrorHandler(backOff);
    }

    private ConsumerFactory<String, AccountCreatedEvent> consumerFactory(
            String bootstrapServers,
            String groupId,
            String autoOffsetReset,
            String maxPollRecords) {
        Map<String, Object> properties =
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG,
                        groupId,
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                        autoOffsetReset,
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                        StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                        JsonDeserializer.class,
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                        false,
                        ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                        maxPollRecords,
                        JsonDeserializer.TRUSTED_PACKAGES,
                        "br.com.bankflow.auth.kafka",
                        JsonDeserializer.VALUE_DEFAULT_TYPE,
                        AccountCreatedEvent.class,
                        JsonDeserializer.USE_TYPE_INFO_HEADERS,
                        false);
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    private void customizeContainer(
            ConcurrentMessageListenerContainer<String, AccountCreatedEvent> container) {
        container
                .getContainerProperties()
                .setCommitLogLevel(org.springframework.kafka.support.LogIfLevelEnabled.Level.INFO);
    }
}

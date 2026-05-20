package br.com.bankflow.accounts.worker.config;

import br.com.bankflow.accounts.shared.kafka.AccountRequestedEvent;
import br.com.bankflow.accounts.shared.kafka.AccountValidateCommand;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, AccountRequestedEvent>
            kafkaListenerContainerFactory(
                    ConsumerFactory<String, AccountRequestedEvent> consumerFactory,
                    DefaultErrorHandler accountRequestedErrorHandler,
                    @Value("${app.kafka.consumer.account-requested.concurrency}") int concurrency) {
        ConcurrentKafkaListenerContainerFactory<String, AccountRequestedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        factory.setCommonErrorHandler(accountRequestedErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setContainerCustomizer(this::customizeContainer);
        return factory;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, AccountValidateCommand>
            accountValidateKafkaListenerContainerFactory(
                    @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                    DefaultErrorHandler accountRequestedErrorHandler,
                    @Value("${app.kafka.consumer.account-validate.concurrency}") int concurrency) {
        ConcurrentKafkaListenerContainerFactory<String, AccountValidateCommand> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(accountValidateConsumerFactory(bootstrapServers));
        factory.setConcurrency(concurrency);
        factory.setCommonErrorHandler(accountRequestedErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setContainerCustomizer(this::customizeAccountValidateContainer);
        return factory;
    }

    @Bean
    DefaultErrorHandler accountRequestedErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${app.kafka.topics.account-requested-dlt}") String dltTopic,
            @Value("${app.kafka.consumer.account-requested.retry.max-attempts}") int maxAttempts,
            @Value("${app.kafka.consumer.account-requested.retry.initial-interval-ms}")
                    long initialIntervalMs,
            @Value("${app.kafka.consumer.account-requested.retry.multiplier}") double multiplier,
            @Value("${app.kafka.consumer.account-requested.retry.max-interval-ms}")
                    long maxIntervalMs) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) -> new TopicPartition(dltTopic, record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
        backOff.setMaxInterval(maxIntervalMs);
        backOff.setMaxElapsedTime(maxIntervalMs * Math.max(1, maxAttempts));
        return new DefaultErrorHandler(recoverer, backOff);
    }

    private void customizeContainer(
            ConcurrentMessageListenerContainer<String, AccountRequestedEvent> container) {
        container
                .getContainerProperties()
                .setCommitLogLevel(org.springframework.kafka.support.LogIfLevelEnabled.Level.INFO);
        container
                .getContainerProperties()
                .getKafkaConsumerProperties()
                .put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
    }

    private ConsumerFactory<String, AccountValidateCommand> accountValidateConsumerFactory(
            String bootstrapServers) {
        Map<String, Object> properties =
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        bootstrapServers,
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                        StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                        JsonDeserializer.class,
                        JsonDeserializer.TRUSTED_PACKAGES,
                        "br.com.bankflow.accounts.shared.kafka",
                        JsonDeserializer.VALUE_DEFAULT_TYPE,
                        AccountValidateCommand.class,
                        JsonDeserializer.USE_TYPE_INFO_HEADERS,
                        false);
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    private void customizeAccountValidateContainer(
            ConcurrentMessageListenerContainer<String, AccountValidateCommand> container) {
        container
                .getContainerProperties()
                .setCommitLogLevel(org.springframework.kafka.support.LogIfLevelEnabled.Level.INFO);
        container
                .getContainerProperties()
                .getKafkaConsumerProperties()
                .put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
    }
}

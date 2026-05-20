package br.com.bankflow.transfers.worker.config;

import br.com.bankflow.transfers.shared.kafka.AccountValidatedEvent;
import br.com.bankflow.transfers.shared.kafka.BalanceHeldEvent;
import br.com.bankflow.transfers.shared.kafka.TransferRequestedEvent;
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
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, TransferRequestedEvent>
            kafkaListenerContainerFactory(
                    ConsumerFactory<String, TransferRequestedEvent> consumerFactory,
                    @Value("${app.kafka.consumer.transfer-requested.concurrency}")
                            int concurrency) {
        ConcurrentKafkaListenerContainerFactory<String, TransferRequestedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setContainerCustomizer(this::customizeContainer);
        return factory;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, BalanceHeldEvent>
            balanceHeldKafkaListenerContainerFactory(
                    @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                    @Value("${spring.kafka.consumer.group-id}") String groupId,
                    @Value("${spring.kafka.consumer.auto-offset-reset}") String autoOffsetReset,
                    @Value("${app.kafka.consumer.balance-held.concurrency}") int concurrency) {
        ConcurrentKafkaListenerContainerFactory<String, BalanceHeldEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(
                balanceHeldConsumerFactory(bootstrapServers, groupId, autoOffsetReset));
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setContainerCustomizer(this::customizeBalanceHeldContainer);
        return factory;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, AccountValidatedEvent>
            accountValidatedKafkaListenerContainerFactory(
                    @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                    @Value("${spring.kafka.consumer.group-id}") String groupId,
                    @Value("${spring.kafka.consumer.auto-offset-reset}") String autoOffsetReset,
                    @Value("${app.kafka.consumer.account-validated.concurrency}") int concurrency) {
        ConcurrentKafkaListenerContainerFactory<String, AccountValidatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(
                accountValidatedConsumerFactory(bootstrapServers, groupId, autoOffsetReset));
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setContainerCustomizer(this::customizeAccountValidatedContainer);
        return factory;
    }

    private void customizeContainer(
            ConcurrentMessageListenerContainer<String, TransferRequestedEvent> container) {
        container
                .getContainerProperties()
                .setCommitLogLevel(org.springframework.kafka.support.LogIfLevelEnabled.Level.INFO);
        container
                .getContainerProperties()
                .getKafkaConsumerProperties()
                .put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
    }

    private ConsumerFactory<String, AccountValidatedEvent> accountValidatedConsumerFactory(
            String bootstrapServers, String groupId, String autoOffsetReset) {
        return consumerFactory(
                bootstrapServers, groupId, autoOffsetReset, AccountValidatedEvent.class);
    }

    private ConsumerFactory<String, BalanceHeldEvent> balanceHeldConsumerFactory(
            String bootstrapServers, String groupId, String autoOffsetReset) {
        return consumerFactory(bootstrapServers, groupId, autoOffsetReset, BalanceHeldEvent.class);
    }

    private <T> ConsumerFactory<String, T> consumerFactory(
            String bootstrapServers, String groupId, String autoOffsetReset, Class<T> eventType) {
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
                        JsonDeserializer.TRUSTED_PACKAGES,
                        "br.com.bankflow.transfers.shared.kafka",
                        JsonDeserializer.VALUE_DEFAULT_TYPE,
                        eventType,
                        JsonDeserializer.USE_TYPE_INFO_HEADERS,
                        false);
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    private void customizeAccountValidatedContainer(
            ConcurrentMessageListenerContainer<String, AccountValidatedEvent> container) {
        container
                .getContainerProperties()
                .setCommitLogLevel(org.springframework.kafka.support.LogIfLevelEnabled.Level.INFO);
        container
                .getContainerProperties()
                .getKafkaConsumerProperties()
                .put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
    }

    private void customizeBalanceHeldContainer(
            ConcurrentMessageListenerContainer<String, BalanceHeldEvent> container) {
        container
                .getContainerProperties()
                .setCommitLogLevel(org.springframework.kafka.support.LogIfLevelEnabled.Level.INFO);
        container
                .getContainerProperties()
                .getKafkaConsumerProperties()
                .put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
    }
}

package br.com.bankflow.balance.worker.config;

import br.com.bankflow.balance.shared.kafka.BalanceCaptureCommand;
import br.com.bankflow.balance.shared.kafka.BalanceHoldCommand;
import br.com.bankflow.balance.shared.kafka.BalanceReleaseCommand;
import br.com.bankflow.balance.shared.kafka.LedgerJournalCreatedEvent;
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
    ConcurrentKafkaListenerContainerFactory<String, LedgerJournalCreatedEvent>
            ledgerJournalCreatedKafkaListenerContainerFactory(
                    @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                    @Value("${spring.kafka.consumer.group-id}") String groupId,
                    @Value("${spring.kafka.consumer.auto-offset-reset}") String autoOffsetReset,
                    @Value("${app.kafka.consumer.ledger-journal-created.concurrency}")
                            int concurrency,
                    @Value("${app.kafka.consumer.max-poll-records}") String maxPollRecords) {
        ConcurrentKafkaListenerContainerFactory<String, LedgerJournalCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(
                ledgerJournalCreatedConsumerFactory(
                        bootstrapServers, groupId, autoOffsetReset, maxPollRecords));
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setContainerCustomizer(this::customizeContainer);
        return factory;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, BalanceCaptureCommand>
            balanceCaptureCommandKafkaListenerContainerFactory(
                    @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                    @Value("${spring.kafka.consumer.group-id}") String groupId,
                    @Value("${spring.kafka.consumer.auto-offset-reset}") String autoOffsetReset,
                    @Value("${app.kafka.consumer.balance-capture-command.concurrency}")
                            int concurrency,
                    @Value("${app.kafka.consumer.max-poll-records}") String maxPollRecords) {
        ConcurrentKafkaListenerContainerFactory<String, BalanceCaptureCommand> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(
                consumerFactory(
                        bootstrapServers,
                        groupId,
                        autoOffsetReset,
                        BalanceCaptureCommand.class,
                        maxPollRecords));
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setContainerCustomizer(this::customizeBalanceCaptureCommandContainer);
        return factory;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, BalanceHoldCommand>
            balanceHoldCommandKafkaListenerContainerFactory(
                    @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                    @Value("${spring.kafka.consumer.group-id}") String groupId,
                    @Value("${spring.kafka.consumer.auto-offset-reset}") String autoOffsetReset,
                    @Value("${app.kafka.consumer.balance-hold-command.concurrency}")
                            int concurrency,
                    @Value("${app.kafka.consumer.max-poll-records}") String maxPollRecords) {
        ConcurrentKafkaListenerContainerFactory<String, BalanceHoldCommand> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(
                consumerFactory(
                        bootstrapServers,
                        groupId,
                        autoOffsetReset,
                        BalanceHoldCommand.class,
                        maxPollRecords));
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setContainerCustomizer(this::customizeBalanceHoldCommandContainer);
        return factory;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, BalanceReleaseCommand>
            balanceReleaseCommandKafkaListenerContainerFactory(
                    @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                    @Value("${spring.kafka.consumer.group-id}") String groupId,
                    @Value("${spring.kafka.consumer.auto-offset-reset}") String autoOffsetReset,
                    @Value("${app.kafka.consumer.balance-release-command.concurrency}")
                            int concurrency,
                    @Value("${app.kafka.consumer.max-poll-records}") String maxPollRecords) {
        ConcurrentKafkaListenerContainerFactory<String, BalanceReleaseCommand> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(
                consumerFactory(
                        bootstrapServers,
                        groupId,
                        autoOffsetReset,
                        BalanceReleaseCommand.class,
                        maxPollRecords));
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setContainerCustomizer(this::customizeBalanceReleaseCommandContainer);
        return factory;
    }

    private ConsumerFactory<String, LedgerJournalCreatedEvent> ledgerJournalCreatedConsumerFactory(
            String bootstrapServers,
            String groupId,
            String autoOffsetReset,
            String maxPollRecords) {
        return consumerFactory(
                bootstrapServers,
                groupId,
                autoOffsetReset,
                LedgerJournalCreatedEvent.class,
                maxPollRecords);
    }

    private <T> ConsumerFactory<String, T> consumerFactory(
            String bootstrapServers,
            String groupId,
            String autoOffsetReset,
            Class<T> eventType,
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
                        "br.com.bankflow.balance.shared.kafka",
                        JsonDeserializer.VALUE_DEFAULT_TYPE,
                        eventType,
                        JsonDeserializer.USE_TYPE_INFO_HEADERS,
                        false);
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    private void customizeContainer(
            ConcurrentMessageListenerContainer<String, LedgerJournalCreatedEvent> container) {
        container
                .getContainerProperties()
                .setCommitLogLevel(org.springframework.kafka.support.LogIfLevelEnabled.Level.INFO);
    }

    private void customizeBalanceHoldCommandContainer(
            ConcurrentMessageListenerContainer<String, BalanceHoldCommand> container) {
        container
                .getContainerProperties()
                .setCommitLogLevel(org.springframework.kafka.support.LogIfLevelEnabled.Level.INFO);
    }

    private void customizeBalanceCaptureCommandContainer(
            ConcurrentMessageListenerContainer<String, BalanceCaptureCommand> container) {
        container
                .getContainerProperties()
                .setCommitLogLevel(org.springframework.kafka.support.LogIfLevelEnabled.Level.INFO);
    }

    private void customizeBalanceReleaseCommandContainer(
            ConcurrentMessageListenerContainer<String, BalanceReleaseCommand> container) {
        container
                .getContainerProperties()
                .setCommitLogLevel(org.springframework.kafka.support.LogIfLevelEnabled.Level.INFO);
    }
}

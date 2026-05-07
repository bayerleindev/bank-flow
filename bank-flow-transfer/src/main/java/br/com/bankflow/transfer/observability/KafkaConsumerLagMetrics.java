package br.com.bankflow.transfer.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class KafkaConsumerLagMetrics implements DisposableBean {
	private final AdminClient adminClient;
	private final String groupId;
	private final List<String> topics;
	private final AtomicLong lag = new AtomicLong();

	public KafkaConsumerLagMetrics(
			MeterRegistry meterRegistry,
			@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
			@Value("${spring.kafka.consumer.group-id}") String groupId,
			@Value("${bank-flow.kafka.topics.ledger-posting-created}") String ledgerPostingCreatedTopic
	) {
		this.groupId = groupId;
		this.topics = List.of(ledgerPostingCreatedTopic);
		Properties properties = new Properties();
		properties.put("bootstrap.servers", bootstrapServers);
		this.adminClient = AdminClient.create(properties);
		Gauge.builder("kafka_consumer_lag", lag, AtomicLong::get)
				.description("Total Kafka consumer lag")
				.tag("service", "bank-flow-transfer")
				.tag("group_id", groupId)
				.register(meterRegistry);
	}

	@Scheduled(fixedDelayString = "${bank-flow.kafka.lag.fixed-delay-ms:15000}")
	public void refreshLag() {
		try {
			List<TopicPartition> partitions = adminClient.describeTopics(topics)
					.allTopicNames()
					.get(5, TimeUnit.SECONDS)
					.values()
					.stream()
					.flatMap(topic -> topic.partitions().stream()
							.map(partition -> new TopicPartition(topic.name(), partition.partition())))
					.toList();
			Map<TopicPartition, OffsetAndMetadata> committed = adminClient.listConsumerGroupOffsets(groupId)
					.partitionsToOffsetAndMetadata()
					.get(5, TimeUnit.SECONDS);
			Map<TopicPartition, OffsetSpec> latestRequests = partitions.stream()
					.collect(Collectors.toMap(partition -> partition, partition -> OffsetSpec.latest()));
			Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest = adminClient.listOffsets(latestRequests)
					.all()
					.get(5, TimeUnit.SECONDS);
			long totalLag = partitions.stream()
					.mapToLong(partition -> {
						long latestOffset = latest.get(partition).offset();
						OffsetAndMetadata committedOffset = committed.get(partition);
						long currentOffset = committedOffset == null ? 0 : committedOffset.offset();
						return Math.max(0, latestOffset - currentOffset);
					})
					.sum();
			lag.set(totalLag);
		} catch (Exception ignored) {
			// Keep the last observed value when Kafka is temporarily unavailable.
		}
	}

	@Override
	public void destroy() {
		adminClient.close(Duration.ofSeconds(5));
	}
}

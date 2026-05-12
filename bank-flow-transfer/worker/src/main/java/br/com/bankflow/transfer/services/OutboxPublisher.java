package br.com.bankflow.transfer.services;

import br.com.bankflow.transfer.domain.OutboxEvent;
import br.com.bankflow.transfer.observability.TransferBusinessMetrics;
import br.com.bankflow.transfer.repositories.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxPublisher {
	private final OutboxEventRepository outboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final TransferBusinessMetrics transferBusinessMetrics;
	private final Clock clock;
	private final int batchSize;
	private final String instanceId;
	private final long lockLeaseMillis;
	private final long sendTimeoutMillis;
	private final int maxAttempts;

	public OutboxPublisher(
			OutboxEventRepository outboxEventRepository,
			KafkaTemplate<String, String> kafkaTemplate,
			TransferBusinessMetrics transferBusinessMetrics,
			Clock clock,
			@Value("${bank-flow.outbox.publisher.batch-size}") int batchSize,
			@Value("${bank-flow.outbox.publisher.instance-id:${HOSTNAME:local}}") String instanceId,
			@Value("${bank-flow.outbox.publisher.lock-lease-ms:60000}") long lockLeaseMillis,
			@Value("${bank-flow.outbox.publisher.send-timeout-ms:30000}") long sendTimeoutMillis,
			@Value("${bank-flow.outbox.publisher.max-attempts:10}") int maxAttempts
	) {
		this.outboxEventRepository = outboxEventRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.transferBusinessMetrics = transferBusinessMetrics;
		this.clock = clock;
		this.batchSize = batchSize;
		this.instanceId = instanceId;
		this.lockLeaseMillis = lockLeaseMillis;
		this.sendTimeoutMillis = sendTimeoutMillis;
		this.maxAttempts = maxAttempts;
	}

	@Scheduled(fixedDelayString = "${bank-flow.outbox.publisher.fixed-delay-ms}")
	public void publishPending() {
		long now = clock.millis();
		for (OutboxEvent event : outboxEventRepository.claimPending(
				batchSize,
				instanceId,
				now,
				now + lockLeaseMillis,
				maxAttempts
		)) {
			publish(event);
		}
	}

	private void publish(OutboxEvent event) {
		ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.eventKey(), event.payload());
		record.headers().add(new RecordHeader("event_name", event.eventType().getBytes(StandardCharsets.UTF_8)));
		record.headers().add(new RecordHeader("content_type", "application/json".getBytes(StandardCharsets.UTF_8)));
		try {
			kafkaTemplate.send(record).get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
			outboxEventRepository.markPublished(event.eventId(), clock.millis(), instanceId);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			outboxEventRepository.markFailed(event.eventId(), exception.getMessage(), instanceId, maxAttempts);
			transferBusinessMetrics.recordOutboxPublishFailure(event.topic(), event.eventType());
			throw new IllegalStateException("interrupted while publishing outbox event", exception);
		} catch (ExecutionException | TimeoutException exception) {
			outboxEventRepository.markFailed(event.eventId(), exception.getMessage(), instanceId, maxAttempts);
			transferBusinessMetrics.recordOutboxPublishFailure(event.topic(), event.eventType());
		}
	}
}

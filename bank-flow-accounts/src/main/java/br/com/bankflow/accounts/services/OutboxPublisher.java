package br.com.bankflow.accounts.services;

import br.com.bankflow.accounts.domain.OutboxEvent;
import br.com.bankflow.accounts.observability.AccountBusinessMetrics;
import br.com.bankflow.accounts.repositories.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.ExecutionException;

@Component
public class OutboxPublisher {
	private final OutboxEventRepository outboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final AccountBusinessMetrics accountBusinessMetrics;
	private final Clock clock;
	private final int batchSize;

	public OutboxPublisher(
			OutboxEventRepository outboxEventRepository,
			KafkaTemplate<String, String> kafkaTemplate,
			AccountBusinessMetrics accountBusinessMetrics,
			Clock clock,
			@Value("${bank-flow.outbox.publisher.batch-size}") int batchSize
	) {
		this.outboxEventRepository = outboxEventRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.accountBusinessMetrics = accountBusinessMetrics;
		this.clock = clock;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${bank-flow.outbox.publisher.fixed-delay-ms}")
	public void publishPending() {
		for (OutboxEvent event : outboxEventRepository.findPending(batchSize)) {
			publish(event);
		}
	}

	private void publish(OutboxEvent event) {
		ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.eventKey(), event.payload());
		record.headers().add(new RecordHeader("event_name", event.eventType().getBytes(StandardCharsets.UTF_8)));
		record.headers().add(new RecordHeader("content_type", "application/json".getBytes(StandardCharsets.UTF_8)));
		try {
			kafkaTemplate.send(record).get();
			outboxEventRepository.markPublished(event.eventId(), clock.millis());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			outboxEventRepository.markFailed(event.eventId(), exception.getMessage());
			accountBusinessMetrics.recordOutboxPublishFailure(event.topic(), event.eventType());
			throw new IllegalStateException("interrupted while publishing outbox event", exception);
		} catch (ExecutionException exception) {
			outboxEventRepository.markFailed(event.eventId(), exception.getMessage());
			accountBusinessMetrics.recordOutboxPublishFailure(event.topic(), event.eventType());
		}
	}
}

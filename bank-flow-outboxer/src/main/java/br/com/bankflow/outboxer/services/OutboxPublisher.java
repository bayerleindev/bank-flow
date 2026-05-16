package br.com.bankflow.outboxer.services;

import br.com.bankflow.outboxer.domain.OutboxEvent;
import br.com.bankflow.outboxer.observability.OutboxerMetrics;
import br.com.bankflow.outboxer.observability.TransferTracing;
import br.com.bankflow.outboxer.repositories.OutboxEventRepository;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxPublisher {
	private final OutboxEventRepository outboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final OutboxerMetrics metrics;
	private final Tracer tracer;
	private final Clock clock;
	private final int batchSize;
	private final String instanceId;
	private final long lockLeaseMillis;
	private final long sendTimeoutMillis;
	private final int maxAttempts;
    private final TransferTracing transferTracing;

	public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxerMetrics metrics,
            ObjectProvider<Tracer> tracerProvider,
            Clock clock,
            @Value("${bank-flow.outbox.publisher.batch-size}") int batchSize,
            @Value("${bank-flow.outbox.publisher.instance-id:${HOSTNAME:local}}") String instanceId,
            @Value("${bank-flow.outbox.publisher.lock-lease-ms:60000}") long lockLeaseMillis,
            @Value("${bank-flow.outbox.publisher.send-timeout-ms:30000}") long sendTimeoutMillis,
            @Value("${bank-flow.outbox.publisher.max-attempts:10}") int maxAttempts, TransferTracing transferTracing
    ) {
		this.outboxEventRepository = outboxEventRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.metrics = metrics;
		this.tracer = tracerProvider.getIfAvailable();
		this.clock = clock;
		this.batchSize = batchSize;
		this.instanceId = instanceId;
		this.lockLeaseMillis = lockLeaseMillis;
		this.sendTimeoutMillis = sendTimeoutMillis;
		this.maxAttempts = maxAttempts;
        this.transferTracing = transferTracing;
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
            publishRecord(event);
		}
	}

	private void publishRecord(OutboxEvent event) {
        transferTracing.withTransferId(UUID.fromString(Objects.requireNonNull(transactionId(event))), () -> {
            ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.eventKey(), event.payload());
            record.headers().add(new RecordHeader("event_name", event.eventType().getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader("content_type", "application/json".getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader("producer_service", event.producerService().getBytes(StandardCharsets.UTF_8)));
            addBusinessHeaders(record, event);
            try {
                kafkaTemplate.send(record).get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
                outboxEventRepository.markPublished(event.eventId(), clock.millis(), instanceId);
                metrics.recordPublished(event.producerService(), event.topic(), event.eventType(), transferIdLabel(event));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                markFailed(event, exception);
                throw new IllegalStateException("interrupted while publishing outbox event", exception);
            } catch (ExecutionException | TimeoutException exception) {
                markFailed(event, exception);
            }
        });
	}

	private void markFailed(OutboxEvent event, Exception exception) {
		outboxEventRepository.markFailed(event.eventId(), exception.getMessage(), instanceId, maxAttempts);
		metrics.recordPublishFailure(event.producerService(), event.topic(), event.eventType());
	}

	private String transferIdLabel(OutboxEvent event) {
		return transactionId(event) == null ? "missing_transfer_id" : "with_transfer_id";
	}

	private void addHeaderIfPresent(ProducerRecord<String, String> record, String name, String value) {
		if (value != null && !value.isBlank()) {
			record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
		}
	}

	private void addBusinessHeaders(ProducerRecord<String, String> record, OutboxEvent event) {
		String transferId = transactionId(event);
        addHeaderIfPresent(record, "event_id", event.eventId().toString());
        addHeaderIfPresent(record, "event_type", event.eventType());
        addHeaderIfPresent(record, "aggregate_type", event.aggregateType());
        addHeaderIfPresent(record, "aggregate_id", event.aggregateId());
        addHeaderIfPresent(record, "producer_service", event.producerService());

        addHeaderIfPresent(record, "transfer_id", transferId);
        addHeaderIfPresent(record, "account_id", event.eventKey());
	}

	private String transactionId(OutboxEvent event) {
		if ("Transfer".equals(event.aggregateType())) {
			return event.aggregateId();
		}
		return null;
	}
}

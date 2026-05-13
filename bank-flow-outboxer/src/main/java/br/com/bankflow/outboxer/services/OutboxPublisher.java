package br.com.bankflow.outboxer.services;

import br.com.bankflow.outboxer.domain.OutboxEvent;
import br.com.bankflow.outboxer.observability.OutboxerMetrics;
import br.com.bankflow.outboxer.repositories.OutboxEventRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
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
			@Value("${bank-flow.outbox.publisher.max-attempts:10}") int maxAttempts
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
		Span span = publisherSpan(event);
		if (span == null) {
			publishRecord(event, null);
			return;
		}
		try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
			publishRecord(event, traceparent(span.context()));
		} catch (RuntimeException exception) {
			span.error(exception);
			throw exception;
		} finally {
			span.end();
		}
	}

	private void publishRecord(OutboxEvent event, String traceparent) {
		ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.eventKey(), event.payload());
		record.headers().add(new RecordHeader("event_name", event.eventType().getBytes(StandardCharsets.UTF_8)));
		record.headers().add(new RecordHeader("content_type", "application/json".getBytes(StandardCharsets.UTF_8)));
		record.headers().add(new RecordHeader("producer_service", event.producerService().getBytes(StandardCharsets.UTF_8)));
		addHeaderIfPresent(record, "traceparent", firstNonBlank(traceparent, event.traceparent()));
		addHeaderIfPresent(record, "tracestate", event.tracestate());
		try {
			kafkaTemplate.send(record).get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
			outboxEventRepository.markPublished(event.eventId(), clock.millis(), instanceId);
			metrics.recordPublished(event.producerService(), event.topic(), event.eventType(), traceContextLabel(firstNonBlank(traceparent, event.traceparent())));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			markFailed(event, exception);
			throw new IllegalStateException("interrupted while publishing outbox event", exception);
		} catch (ExecutionException | TimeoutException exception) {
			markFailed(event, exception);
		}
	}

	private Span publisherSpan(OutboxEvent event) {
		if (tracer == null) {
			return null;
		}
		TraceContext parent = traceContext(event.traceparent());
		if (parent == null) {
			return null;
		}
		return tracer.spanBuilder()
				.setParent(parent)
				.name("outbox publish")
				.kind(Span.Kind.PRODUCER)
				.tag("messaging.system", "kafka")
				.tag("messaging.destination.name", event.topic())
				.tag("outbox.event_type", event.eventType())
				.tag("outbox.producer_service", event.producerService())
				.start();
	}

	private TraceContext traceContext(String traceparent) {
		if (traceparent == null || traceparent.isBlank()) {
			return null;
		}
		String[] parts = traceparent.split("-");
		if (parts.length != 4 || parts[1].length() != 32 || parts[2].length() != 16) {
			return null;
		}
		return tracer.traceContextBuilder()
				.traceId(parts[1])
				.spanId(parts[2])
				.sampled("01".equals(parts[3]))
				.build();
	}

	private String traceparent(TraceContext context) {
		return "00-%s-%s-%s".formatted(
				context.traceId(),
				context.spanId(),
				Boolean.TRUE.equals(context.sampled()) ? "01" : "00"
		);
	}

	private void markFailed(OutboxEvent event, Exception exception) {
		outboxEventRepository.markFailed(event.eventId(), exception.getMessage(), instanceId, maxAttempts);
		metrics.recordPublishFailure(event.producerService(), event.topic(), event.eventType());
	}

	private String firstNonBlank(String primary, String fallback) {
		return primary != null && !primary.isBlank() ? primary : fallback;
	}

	private String traceContextLabel(String traceparent) {
		return traceparent == null || traceparent.isBlank() ? "missing_trace" : "with_trace";
	}

	private void addHeaderIfPresent(ProducerRecord<String, String> record, String name, String value) {
		if (value != null && !value.isBlank()) {
			record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
		}
	}
}

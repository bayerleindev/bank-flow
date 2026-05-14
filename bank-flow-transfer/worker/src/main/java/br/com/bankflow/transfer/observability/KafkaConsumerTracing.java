package br.com.bankflow.transfer.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class KafkaConsumerTracing {
	private final Tracer tracer;
	private final String groupId;

	public KafkaConsumerTracing(
			ObjectProvider<Tracer> tracerProvider,
			@Value("${spring.kafka.consumer.group-id}") String groupId
	) {
		this.tracer = tracerProvider.getIfAvailable();
		this.groupId = groupId;
	}

	public void consume(ConsumerRecord<String, String> record, String fallbackEventType, ThrowingOperation operation) throws Exception {
		Span span = consumerSpan(record, fallbackEventType);
		if (span == null) {
			operation.run();
			return;
		}
		try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
			operation.run();
		} catch (Exception exception) {
			span.error(exception);
			throw exception;
		} finally {
			span.end();
		}
	}

	private Span consumerSpan(ConsumerRecord<String, String> record, String fallbackEventType) {
		if (tracer == null) {
			return null;
		}
		String eventType = firstNonBlank(headerValue(record, "event_name"), fallbackEventType);
		Span currentSpan = tracer.currentSpan();
		Span.Builder builder = tracer.spanBuilder();
		if (currentSpan != null) {
			builder.setParent(currentSpan.context());
		}
		return builder
				.name("%s consume %s".formatted(record.topic(), eventType))
				.kind(Span.Kind.CONSUMER)
				.tag("messaging.system", "kafka")
				.tag("messaging.operation", "process")
				.tag("messaging.destination.name", record.topic())
				.tag("messaging.kafka.consumer.group", groupId)
				.tag("messaging.kafka.message.key", firstNonBlank(record.key(), "none"))
				.tag("messaging.kafka.partition", String.valueOf(record.partition()))
				.tag("messaging.kafka.offset", String.valueOf(record.offset()))
				.tag("event.name", eventType)
				.start();
	}

	private String headerValue(ConsumerRecord<String, String> record, String name) {
		Header header = record.headers().lastHeader(name);
		if (header == null || header.value() == null || header.value().length == 0) {
			return null;
		}
		String value = new String(header.value(), StandardCharsets.UTF_8);
		return value.isBlank() ? null : value;
	}

	private String firstNonBlank(String primary, String fallback) {
		return primary != null && !primary.isBlank() ? primary : fallback;
	}

	@FunctionalInterface
	public interface ThrowingOperation {
		void run() throws Exception;
	}
}

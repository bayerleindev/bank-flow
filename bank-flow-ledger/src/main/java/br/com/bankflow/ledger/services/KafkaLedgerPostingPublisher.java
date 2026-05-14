package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.LedgerEntry;
import br.com.bankflow.ledger.domain.LedgerEntryLine;
import br.com.bankflow.ledger.domain.LedgerPosting;
import br.com.bankflow.ledger.observability.KafkaTraceContext;
import br.com.bankflow.ledger.observability.LedgerBusinessMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
public class KafkaLedgerPostingPublisher implements LedgerPostingPublisher {
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final LedgerBusinessMetrics ledgerBusinessMetrics;
	private final Clock clock;
	private final String topic;
	private final Tracer tracer;

	public KafkaLedgerPostingPublisher(
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper,
			LedgerBusinessMetrics ledgerBusinessMetrics,
			Clock clock,
			ObjectProvider<Tracer> tracerProvider,
			@Value("${bank-flow.kafka.topics.ledger-posting-created}") String topic
	) {
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.ledgerBusinessMetrics = ledgerBusinessMetrics;
		this.clock = clock;
		this.topic = topic;
		this.tracer = tracerProvider.getIfAvailable();
	}

	@Override
	public void publish(LedgerPosting posting) throws JsonProcessingException {
		Span span = publisherSpan(posting);
		if (span == null) {
			publishRecord(posting);
			return;
		}
		try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
			publishRecord(posting);
		} catch (JsonProcessingException exception) {
			span.error(exception);
			throw exception;
		} catch (RuntimeException exception) {
			span.error(exception);
			throw exception;
		} finally {
			span.end();
		}
	}

	private void publishRecord(LedgerPosting posting) throws JsonProcessingException {
		String key = posting.entry().externalId();
		String value = objectMapper.writeValueAsString(toEvent(posting));
		ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
		record.headers().add(new RecordHeader("event_name", "ledger.posting_created".getBytes(StandardCharsets.UTF_8)));
		record.headers().add(new RecordHeader("content_type", "application/json".getBytes(StandardCharsets.UTF_8)));
		record.headers().add(new RecordHeader("entry_type", posting.entry().entryType().getBytes(StandardCharsets.UTF_8)));
		addCurrentTraceHeaders(record);

		try {
			kafkaTemplate.send(record).get();
			ledgerBusinessMetrics.recordLedgerPostingCreated(posting.entry().entryType());
			ledgerBusinessMetrics.recordLedgerPostingLatency(
					clock.millis() - posting.entry().occurredAt(),
					posting.entry().entryType()
			);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			ledgerBusinessMetrics.recordLedgerPublishFailure(topic, posting.entry().entryType(), exception.getClass().getSimpleName());
			throw new IllegalStateException("interrupted while publishing ledger posting event", exception);
		} catch (ExecutionException exception) {
			ledgerBusinessMetrics.recordLedgerPublishFailure(topic, posting.entry().entryType(), rootCauseName(exception));
			throw new IllegalStateException("failed to publish ledger posting event", exception);
		}
	}

	private Span publisherSpan(LedgerPosting posting) {
		if (tracer == null) {
			return null;
		}
		Span.Builder builder = tracer.spanBuilder();
		TraceContext parent = traceContext(KafkaTraceContext.traceparent());
		if (parent != null && tracer.currentSpan() == null) {
			builder.setParent(parent);
		}
		return builder
				.name("%s publish ledger.posting_created".formatted(topic))
				.kind(Span.Kind.PRODUCER)
				.tag("messaging.system", "kafka")
				.tag("messaging.operation", "publish")
				.tag("messaging.destination.name", topic)
				.tag("messaging.kafka.message.key", posting.entry().externalId())
				.tag("event.name", "ledger.posting_created")
				.tag("ledger.entry_type", posting.entry().entryType())
				.start();
	}

	private String rootCauseName(Throwable exception) {
		Throwable current = exception;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getClass().getSimpleName();
	}

	private void addCurrentTraceHeaders(ProducerRecord<String, String> record) {
		Span currentSpan = tracer == null ? null : tracer.currentSpan();
		if (currentSpan != null) {
			TraceContext context = currentSpan.context();
			addHeader(record, "traceparent", traceparent(context));
			return;
		}
		addPropagatedTraceHeaders(record);
	}

	private void addPropagatedTraceHeaders(ProducerRecord<String, String> record) {
		String traceparent = KafkaTraceContext.traceparent();
		if (traceparent == null || traceparent.isBlank()) {
			return;
		}
		addHeader(record, "traceparent", traceparent);
		String tracestate = KafkaTraceContext.tracestate();
		if (tracestate != null && !tracestate.isBlank()) {
			addHeader(record, "tracestate", tracestate);
		}
	}

	private String traceparent(TraceContext context) {
		String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
		return "00-%s-%s-%s".formatted(context.traceId(), context.spanId(), flags);
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

	private void addHeader(ProducerRecord<String, String> record, String name, String value) {
		record.headers().remove(name);
		record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
	}

	private Map<String, Object> toEvent(LedgerPosting posting) {
		LedgerEntry entry = posting.entry();
		Map<String, Object> metadata = readMetadata(entry.metadata());
		return Map.of(
				"entry_id", entry.entryId(),
				"external_id", entry.externalId(),
				"entry_type", entry.entryType(),
				"status", entry.status(),
				"description", entry.description(),
				"occurred_at", entry.occurredAt(),
				"created_at", entry.createdAt(),
				"reversal_of_entry_id", entry.reversalOfEntryId(),
				"metadata", entry.metadata(),
				"lines", posting.lines().stream().map(line -> toLineEvent(entry, line, metadata)).toList()
		);
	}

	private Map<String, Object> toLineEvent(LedgerEntry entry, LedgerEntryLine line, Map<String, Object> metadata) {
		return Map.of(
				"line_id", line.lineId(),
				"entry_id", line.entryId(),
				"account_id", line.accountId(),
				"digital_account_id", digitalAccountIdFor(entry, line, metadata),
				"direction", line.direction(),
				"amount_minor", line.amountMinor(),
				"signed_amount_minor", line.signedAmountMinor(),
				"currency", line.currency(),
				"line_memo", line.lineMemo(),
				"created_at", line.createdAt()
		);
	}

	private String digitalAccountIdFor(LedgerEntry entry, LedgerEntryLine line, Map<String, Object> metadata) {
		if ("YIELD_CDI".equals(entry.entryType())) {
			String key = "DEBIT".equals(line.direction()) ? "interest_expense_digital_account_id" : "digital_account_id";
			Object value = metadata.get(key);
			if (value == null) {
				throw new IllegalStateException("ledger posting metadata missing " + key);
			}
			return value.toString();
		}
		String key = "DEBIT".equals(line.direction()) ? "source_digital_account_id" : "destination_digital_account_id";
		Object value = metadata.get(key);
		if (value == null) {
			throw new IllegalStateException("ledger posting metadata missing " + key);
		}
		return value.toString();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> readMetadata(String metadata) {
		try {
			return objectMapper.readValue(metadata, Map.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to parse ledger posting metadata", exception);
		}
	}
}

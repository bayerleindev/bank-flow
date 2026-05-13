package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.LedgerEntry;
import br.com.bankflow.ledger.domain.LedgerEntryLine;
import br.com.bankflow.ledger.domain.LedgerPosting;
import br.com.bankflow.ledger.observability.LedgerBusinessMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
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

	public KafkaLedgerPostingPublisher(
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper,
			LedgerBusinessMetrics ledgerBusinessMetrics,
			Clock clock,
			@Value("${bank-flow.kafka.topics.ledger-posting-created}") String topic
	) {
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.ledgerBusinessMetrics = ledgerBusinessMetrics;
		this.clock = clock;
		this.topic = topic;
	}

	@Override
	public void publish(LedgerPosting posting) throws JsonProcessingException {
		String key = posting.entry().externalId();
		String value = objectMapper.writeValueAsString(toEvent(posting));
		ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
		record.headers().add(new RecordHeader("event_name", "ledger.posting_created".getBytes(StandardCharsets.UTF_8)));
		record.headers().add(new RecordHeader("content_type", "application/json".getBytes(StandardCharsets.UTF_8)));

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

	private String rootCauseName(Throwable exception) {
		Throwable current = exception;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getClass().getSimpleName();
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

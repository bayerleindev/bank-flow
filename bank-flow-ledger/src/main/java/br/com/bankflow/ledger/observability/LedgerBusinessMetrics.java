package br.com.bankflow.ledger.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LedgerBusinessMetrics {
	private final MeterRegistry meterRegistry;

	public LedgerBusinessMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	public void recordLedgerPostingCreated(String entryType) {
		Counter.builder("ledger_posting_created")
				.description("Ledger postings created and published")
				.tag("entry_type", entryType == null ? "unknown" : entryType)
				.register(meterRegistry)
				.increment();
	}

	public void recordKafkaTransferIdContext(String topic, String transferIdContext) {
		Counter.builder("ledger_kafka_transfer_id_context")
				.description("Transfer id coverage on Kafka records consumed by ledger")
				.tag("topic", topic == null ? "unknown" : topic)
				.tag("transfer_id_context", transferIdContext == null ? "unknown" : transferIdContext)
				.register(meterRegistry)
				.increment();
	}

	public void recordLedgerPostingLatency(long latencyMillis, String entryType) {
		Timer.builder("ledger_posting_latency")
				.description("Latency from ledger posting occurrence to publication")
				.tag("entry_type", entryType == null ? "unknown" : entryType)
				.register(meterRegistry)
				.record(Duration.ofMillis(Math.max(0, latencyMillis)));
	}

	public void recordLedgerPublishFailure(String topic, String entryType, String exception) {
		Counter.builder("ledger_publish_failures")
				.description("Ledger posting publish failures")
				.tag("topic", topic == null ? "unknown" : topic)
				.tag("entry_type", entryType == null ? "unknown" : entryType)
				.tag("exception", exception == null ? "unknown" : exception)
				.register(meterRegistry)
				.increment();
	}

	public void recordValidationFailure(String operation, String reason) {
		Counter.builder("ledger_validation_failures")
				.description("Ledger validation failures")
				.tag("operation", operation == null ? "unknown" : operation)
				.tag("reason", sanitize(reason))
				.register(meterRegistry)
				.increment();
	}

	public void recordIdempotencyHit(String operation) {
		Counter.builder("ledger_idempotency_hits")
				.description("Ledger commands ignored because they were already processed")
				.tag("operation", operation == null ? "unknown" : operation)
				.register(meterRegistry)
				.increment();
	}

	public void recordLedgerReversalCreated() {
		Counter.builder("ledger_reversals_created")
				.description("Ledger reversals created and published")
				.register(meterRegistry)
				.increment();
	}

	public void recordLedgerPostingUnbalanced(String entryType) {
		Counter.builder("ledger_posting_unbalanced")
				.description("Ledger postings rejected because debit and credit lines were not balanced")
				.tag("entry_type", entryType == null ? "unknown" : entryType)
				.register(meterRegistry)
				.increment();
	}

	public void recordLedgerPostingBalanceDifference(long differenceMinor, String entryType) {
		DistributionSummary.builder("ledger_posting_balance_difference_minor")
				.description("Absolute debit and credit difference per attempted ledger posting")
				.tag("entry_type", entryType == null ? "unknown" : entryType)
				.baseUnit("minor_units")
				.register(meterRegistry)
				.record(Math.max(0, differenceMinor));
	}

	private String sanitize(String reason) {
		if (reason == null || reason.isBlank()) {
			return "unknown";
		}
		return reason.replaceAll("[^a-zA-Z0-9_]+", "_").toLowerCase();
	}
}

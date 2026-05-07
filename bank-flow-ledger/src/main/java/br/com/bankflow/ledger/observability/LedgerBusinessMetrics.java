package br.com.bankflow.ledger.observability;

import io.micrometer.core.instrument.Counter;
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

	public void recordLedgerPostingLatency(long latencyMillis, String entryType) {
		Timer.builder("ledger_posting_latency")
				.description("Latency from ledger posting occurrence to publication")
				.tag("entry_type", entryType == null ? "unknown" : entryType)
				.register(meterRegistry)
				.record(Duration.ofMillis(Math.max(0, latencyMillis)));
	}
}

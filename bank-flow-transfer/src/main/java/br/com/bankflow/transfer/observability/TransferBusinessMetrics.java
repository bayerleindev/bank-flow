package br.com.bankflow.transfer.observability;

import br.com.bankflow.transfer.repositories.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class TransferBusinessMetrics {
	private final MeterRegistry meterRegistry;

	public TransferBusinessMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
		this(meterRegistry, outboxEventRepository, Clock.systemUTC());
	}

	@Autowired
	public TransferBusinessMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository, Clock clock) {
		this.meterRegistry = meterRegistry;
		Gauge.builder("outbox_pending_events", outboxEventRepository::countPending)
				.description("Pending outbox events")
				.tag("service", "bank-flow-transfer")
				.register(meterRegistry);
		Gauge.builder("outbox_oldest_pending_event_age_seconds",
						() -> outboxEventRepository.oldestPendingEventAgeSeconds(clock.millis()))
				.description("Age of the oldest pending outbox event")
				.tag("service", "bank-flow-transfer")
				.register(meterRegistry);
	}

	public void recordTransferCreated() {
		Counter.builder("transfers_created")
				.description("Created transfers")
				.register(meterRegistry)
				.increment();
	}

	public void recordTransferCompleted() {
		Counter.builder("transfers_completed")
				.description("Completed transfers")
				.register(meterRegistry)
				.increment();
	}

	public void recordTransferFailed(String reason) {
		Counter.builder("transfers_failed")
				.description("Failed transfers")
				.tag("reason", reason == null ? "unknown" : reason)
				.register(meterRegistry)
				.increment();
	}

	public void recordTransferPspConfirmed() {
		Counter.builder("transfer_psp_confirmed")
				.description("PSP-confirmed transfers")
				.register(meterRegistry)
				.increment();
	}

	public void recordOutboxPublishFailure(String topic, String eventType) {
		Counter.builder("outbox_publish_failures")
				.description("Outbox publish failures")
				.tag("service", "bank-flow-transfer")
				.tag("topic", topic)
				.tag("event_type", eventType)
				.register(meterRegistry)
				.increment();
	}
}

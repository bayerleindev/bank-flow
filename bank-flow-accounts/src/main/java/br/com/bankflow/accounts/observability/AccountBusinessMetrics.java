package br.com.bankflow.accounts.observability;

import br.com.bankflow.accounts.repositories.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class AccountBusinessMetrics {
	private final MeterRegistry meterRegistry;

	public AccountBusinessMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
		this(meterRegistry, outboxEventRepository, Clock.systemUTC());
	}

	@Autowired
	public AccountBusinessMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository, Clock clock) {
		this.meterRegistry = meterRegistry;
		Gauge.builder("outbox_pending_events", outboxEventRepository::countPending)
				.description("Pending outbox events")
				.tag("service", "bank-flow-accounts")
				.register(meterRegistry);
		Gauge.builder("outbox_oldest_pending_event_age_seconds",
						() -> outboxEventRepository.oldestPendingEventAgeSeconds(clock.millis()))
				.description("Age of the oldest pending outbox event")
				.tag("service", "bank-flow-accounts")
				.register(meterRegistry);
	}

	public void recordAccountCreated() {
		Counter.builder("accounts_created")
				.description("Created active digital accounts")
				.register(meterRegistry)
				.increment();
	}

	public void recordOutboxPublishFailure(String topic, String eventType) {
		Counter.builder("outbox_publish_failures")
				.description("Outbox publish failures")
				.tag("service", "bank-flow-accounts")
				.tag("topic", topic)
				.tag("event_type", eventType)
				.register(meterRegistry)
				.increment();
	}
}

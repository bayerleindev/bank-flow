package br.com.bankflow.outboxer.observability;

import br.com.bankflow.outboxer.repositories.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class OutboxerMetrics {
	private final MeterRegistry meterRegistry;

	public OutboxerMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository, Clock clock) {
		this.meterRegistry = meterRegistry;
		io.micrometer.core.instrument.Gauge.builder("outbox_pending_events", outboxEventRepository::countPending)
				.description("Pending outbox events")
				.register(meterRegistry);
		io.micrometer.core.instrument.Gauge.builder("outbox_oldest_pending_event_age_seconds",
						() -> outboxEventRepository.oldestPendingEventAgeSeconds(clock.millis()))
				.description("Age of the oldest pending outbox event")
				.register(meterRegistry);
	}

	public void recordPublishFailure(String producerService, String topic, String eventType) {
		Counter.builder("outbox_publish_failures")
				.description("Outbox publish failures")
				.tag("producer_service", producerService)
				.tag("topic", topic)
				.tag("event_type", eventType)
				.register(meterRegistry)
				.increment();
	}
}

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

	public void recordPublished(String producerService, String topic, String eventType, String transferIdContext) {
		Counter.builder("outbox_published_events")
				.description("Outbox events published to Kafka")
				.tag("producer_service", producerService == null ? "unknown" : producerService)
				.tag("topic", topic == null ? "unknown" : topic)
				.tag("event_type", eventType == null ? "unknown" : eventType)
				.tag("transfer_id_context", transferIdContext == null ? "unknown" : transferIdContext)
				.register(meterRegistry)
				.increment();
	}
}

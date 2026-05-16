package br.com.bankflow.transfer.observability;

import br.com.bankflow.transfer.domain.TransferStatus;
import br.com.bankflow.transfer.repositories.OutboxEventRepository;
import br.com.bankflow.transfer.repositories.TransferRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class TransferBusinessMetrics {
	private final MeterRegistry meterRegistry;
	private final Timer transferEndToEndLatency;
	private final String serviceName;

	public TransferBusinessMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
		this(meterRegistry, outboxEventRepository, null, Clock.systemUTC(), "bank-flow-transfer", true);
	}

	@Autowired
	public TransferBusinessMetrics(
			MeterRegistry meterRegistry,
			OutboxEventRepository outboxEventRepository,
			TransferRepository transferRepository,
			Clock clock,
			@Value("${spring.application.name:bank-flow-transfer}") String serviceName,
			@Value("${bank-flow.outbox.metrics.enabled:true}") boolean outboxMetricsEnabled
	) {
		this.meterRegistry = meterRegistry;
		this.serviceName = serviceName;
		this.transferEndToEndLatency = Timer.builder("transfer_end_to_end_latency")
				.description("End-to-end latency from transfer creation to completion")
				.publishPercentileHistogram()
				.publishPercentiles(0.5, 0.95, 0.99)
				.register(meterRegistry);
		if (outboxMetricsEnabled) {
			Gauge.builder("outbox_pending_events", outboxEventRepository::countPending)
					.description("Pending outbox events")
					.tag("service", serviceName)
					.register(meterRegistry);
			Gauge.builder("outbox_oldest_pending_event_age_seconds",
							() -> outboxEventRepository.oldestPendingEventAgeSeconds(clock.millis()))
					.description("Age of the oldest pending outbox event")
					.tag("service", serviceName)
					.register(meterRegistry);
		}
		if (transferRepository != null) {
			registerTransferStateGauges(transferRepository, clock);
		}
	}

	private void registerTransferStateGauges(TransferRepository transferRepository, Clock clock) {
		for (TransferStatus status : TransferStatus.values()) {
			Gauge.builder("transfers_in_status", transferRepository, repository -> repository.countByStatus(status))
					.description("Current transfers by status")
					.tag("status", status.name())
					.register(meterRegistry);
			Gauge.builder("transfer_oldest_in_status_age_seconds", transferRepository,
							repository -> repository.oldestUpdatedAtByStatus(status)
									.stream()
									.mapToDouble(updatedAt -> Math.max(0, clock.millis() - updatedAt) / 1000.0)
									.findFirst()
									.orElse(0.0))
					.description("Age of the oldest transfer currently in each status")
					.tag("status", status.name())
					.register(meterRegistry);
		}
	}

	public void recordTransferCreated() {
		Counter.builder("transfers_created")
				.description("Created transfers")
				.tag("flow", "internal")
				.register(meterRegistry)
				.increment();
	}

	public void recordExternalInboundTransferCreated() {
		Counter.builder("transfers_created")
				.description("Created transfers")
				.tag("flow", "external_inbound")
				.register(meterRegistry)
				.increment();
		Counter.builder("external_inbound_transfers_created")
				.description("Created external inbound transfers")
				.register(meterRegistry)
				.increment();
	}

	public void recordTransferCompleted() {
		Counter.builder("transfers_completed")
				.description("Completed transfers")
				.register(meterRegistry)
				.increment();
	}

	public void recordTransferEndToEndLatency(long latencyMillis) {
		transferEndToEndLatency.record(Duration.ofMillis(Math.max(0, latencyMillis)));
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

	public void recordTransferIdContext(String stage, String result) {
		Counter.builder("transfer_id_context")
				.description("Transfer id coverage by stage")
				.tag("stage", stage == null ? "unknown" : stage)
				.tag("result", result == null ? "unknown" : result)
				.register(meterRegistry)
				.increment();
	}

	public void recordOutboxPublishFailure(String topic, String eventType) {
		Counter.builder("outbox_publish_failures")
				.description("Outbox publish failures")
				.tag("service", serviceName)
				.tag("topic", topic)
				.tag("event_type", eventType)
				.register(meterRegistry)
				.increment();
	}
}

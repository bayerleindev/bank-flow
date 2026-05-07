package br.com.bankflow.accounts.observability;

import br.com.bankflow.accounts.domain.AccountStatus;
import br.com.bankflow.accounts.repositories.AccountRepository;
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
		this(meterRegistry, outboxEventRepository, null, Clock.systemUTC());
	}

	@Autowired
	public AccountBusinessMetrics(
			MeterRegistry meterRegistry,
			OutboxEventRepository outboxEventRepository,
			AccountRepository accountRepository,
			Clock clock
	) {
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
		if (accountRepository != null) {
			registerAccountStateGauges(accountRepository, clock);
		}
	}

	private void registerAccountStateGauges(AccountRepository accountRepository, Clock clock) {
		for (AccountStatus status : AccountStatus.values()) {
			Gauge.builder("accounts_in_status", accountRepository, repository -> repository.countByStatus(status))
					.description("Current accounts by status")
					.tag("status", status.name())
					.register(meterRegistry);
			Gauge.builder("account_oldest_in_status_age_seconds", accountRepository,
							repository -> repository.oldestUpdatedAtByStatus(status)
									.stream()
									.mapToDouble(updatedAt -> Math.max(0, clock.millis() - updatedAt) / 1000.0)
									.findFirst()
									.orElse(0.0))
					.description("Age of the oldest account currently in each status")
					.tag("status", status.name())
					.register(meterRegistry);
		}
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

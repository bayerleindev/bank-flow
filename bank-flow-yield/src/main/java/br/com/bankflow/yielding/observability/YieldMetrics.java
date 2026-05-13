package br.com.bankflow.yielding.observability;

import br.com.bankflow.yielding.repositories.YieldRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class YieldMetrics {
	private static final String[] STATUSES = {"PENDING", "POSTING_REQUESTED", "POSTED"};

	public YieldMetrics(MeterRegistry meterRegistry, YieldRepository yieldRepository, Clock clock) {
		for (String status : STATUSES) {
			Gauge.builder("yield_accruals_in_status", yieldRepository, repository -> repository.countAccrualsByStatus(status))
					.description("Current yield accruals by status")
					.tag("status", status)
					.register(meterRegistry);
			Gauge.builder("yield_oldest_accrual_in_status_age_seconds", yieldRepository,
							repository -> repository.oldestAccrualAgeSecondsByStatus(status, clock.millis()))
					.description("Age of the oldest yield accrual by status")
					.tag("status", status)
					.register(meterRegistry);
		}
		Gauge.builder("yield_cdi_rates_total", yieldRepository, YieldRepository::countCdiRates)
				.description("Stored CDI rates used by yield accruals")
				.register(meterRegistry);
	}
}

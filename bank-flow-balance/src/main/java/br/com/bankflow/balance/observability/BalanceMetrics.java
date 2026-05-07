package br.com.bankflow.balance.observability;

import br.com.bankflow.balance.services.LedgerPostingProjectionResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class BalanceMetrics {
	private final MeterRegistry meterRegistry;
	private final AtomicLong projectionLagSeconds = new AtomicLong();

	public BalanceMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
		Gauge.builder("balance_projection_lag_seconds", projectionLagSeconds, AtomicLong::get)
				.description("Age of the last successfully projected ledger event")
				.register(meterRegistry);
	}

	@Autowired
	public BalanceMetrics(MeterRegistry meterRegistry, JdbcTemplate jdbcTemplate) {
		this.meterRegistry = meterRegistry;
		Gauge.builder("balance_projection_lag_seconds", projectionLagSeconds, AtomicLong::get)
				.description("Age of the last successfully projected ledger event")
				.register(meterRegistry);
		Gauge.builder("balance_available_minor", () -> sum(jdbcTemplate, "posted_minor - held_minor"))
				.description("Total available balance in minor units")
				.register(meterRegistry);
		Gauge.builder("balance_held_minor", () -> sum(jdbcTemplate, "held_minor"))
				.description("Total held balance in minor units")
				.register(meterRegistry);
	}

	public void recordKafkaMessageReceived(ConsumerRecord<String, String> record) {
		Counter.builder("bank_flow_balance_kafka_messages_total")
				.description("Kafka messages received by bank-flow-balance")
				.tag("topic", record.topic())
				.tag("result", "received")
				.tag("exception", "none")
				.register(meterRegistry)
				.increment();
	}

	public void recordKafkaMessageFailed(ConsumerRecord<String, String> record, Exception exception) {
		Counter.builder("bank_flow_balance_kafka_messages_total")
				.description("Kafka messages received by bank-flow-balance")
				.tag("topic", record.topic())
				.tag("result", "failed")
				.tag("exception", exception.getClass().getSimpleName())
				.register(meterRegistry)
				.increment();
	}

	public Timer.Sample startProjectionTimer() {
		return Timer.start(meterRegistry);
	}

	public void recordProjection(Timer.Sample sample, LedgerPostingProjectionResult result, int lineCount) {
		sample.stop(Timer.builder("bank_flow_balance_projection_duration")
				.description("Ledger posting projection duration")
				.tag("result", result.name().toLowerCase())
				.tag("exception", "none")
				.register(meterRegistry));
		Counter.builder("bank_flow_balance_projection_total")
				.description("Ledger posting projections by result")
				.tag("result", result.name().toLowerCase())
				.tag("exception", "none")
				.register(meterRegistry)
				.increment();
		meterRegistry.summary("bank_flow_balance_projection_lines")
				.record(lineCount);
	}

	public void recordProjectionFailure(Timer.Sample sample, Exception exception) {
		sample.stop(Timer.builder("bank_flow_balance_projection_duration")
				.description("Ledger posting projection duration")
				.tag("result", "failed")
				.tag("exception", exception.getClass().getSimpleName())
				.register(meterRegistry));
		Counter.builder("bank_flow_balance_projection_total")
				.description("Ledger posting projections by result")
				.tag("result", "failed")
				.tag("exception", exception.getClass().getSimpleName())
				.register(meterRegistry)
				.increment();
		Counter.builder("balance_projection_failures")
				.description("Balance projection failures")
				.tag("exception", exception.getClass().getSimpleName())
				.register(meterRegistry)
				.increment();
	}

	public void recordHoldCreated() {
		Counter.builder("balance_holds_created")
				.description("Balance holds created")
				.register(meterRegistry)
				.increment();
	}

	public void recordHoldCaptured() {
		Counter.builder("balance_holds_captured")
				.description("Balance holds captured")
				.register(meterRegistry)
				.increment();
	}

	public void recordHoldReleased() {
		Counter.builder("balance_holds_released")
				.description("Balance holds released")
				.register(meterRegistry)
				.increment();
	}

	public void recordHoldsExpired(long count) {
		if (count <= 0) {
			return;
		}
		Counter.builder("balance_holds_expired")
				.description("Balance holds expired")
				.register(meterRegistry)
				.increment(count);
	}

	public void recordHoldCloseFailure(String operation, String reason) {
		Counter.builder("balance_hold_close_failures")
				.description("Balance hold capture/release failures")
				.tag("operation", operation == null ? "unknown" : operation)
				.tag("reason", reason == null || reason.isBlank() ? "unknown" : reason)
				.register(meterRegistry)
				.increment();
	}

	public void recordProjectionLag(long lagMillis) {
		projectionLagSeconds.set(Math.max(0, lagMillis) / 1000);
	}

	private double sum(JdbcTemplate jdbcTemplate, String expression) {
		Double value = jdbcTemplate.queryForObject(
				"SELECT COALESCE(SUM(%s), 0) FROM account_balances".formatted(expression),
				Double.class
		);
		return value == null ? 0 : value;
	}
}

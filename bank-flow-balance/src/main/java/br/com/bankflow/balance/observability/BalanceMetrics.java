package br.com.bankflow.balance.observability;

import br.com.bankflow.balance.services.LedgerPostingProjectionResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
public class BalanceMetrics {
	private final MeterRegistry meterRegistry;

	public BalanceMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
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
	}
}

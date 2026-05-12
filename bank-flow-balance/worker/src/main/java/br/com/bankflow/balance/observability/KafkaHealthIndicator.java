package br.com.bankflow.balance.observability;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "management.health.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaHealthIndicator implements HealthIndicator {
	private final String bootstrapServers;
	private final long timeoutMs;

	public KafkaHealthIndicator(
			@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
			@Value("${bank-flow.health.kafka.timeout-ms:2000}") long timeoutMs
	) {
		this.bootstrapServers = bootstrapServers;
		this.timeoutMs = timeoutMs;
	}

	@Override
	public Health health() {
		try (AdminClient adminClient = AdminClient.create(Map.of(
				AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
				bootstrapServers,
				AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,
				(int) timeoutMs,
				AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,
				(int) timeoutMs
		))) {
			String clusterId = adminClient.describeCluster()
					.clusterId()
					.get(timeoutMs, TimeUnit.MILLISECONDS);
			return Health.up()
					.withDetail("clusterId", clusterId)
					.withDetail("bootstrapServers", bootstrapServers)
					.withDetail("timeout", Duration.ofMillis(timeoutMs).toString())
					.build();
		} catch (Exception exception) {
			return Health.down(exception)
					.withDetail("bootstrapServers", bootstrapServers)
					.withDetail("timeout", Duration.ofMillis(timeoutMs).toString())
					.build();
		}
	}
}

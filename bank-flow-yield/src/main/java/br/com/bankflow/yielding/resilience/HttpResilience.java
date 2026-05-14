package br.com.bankflow.yielding.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class HttpResilience {
	private final Map<String, Retry> retries = new ConcurrentHashMap<>();
	private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
	private final RetryConfig retryConfig;
	private final CircuitBreakerConfig circuitBreakerConfig;

	public HttpResilience(
			@Value("${bank-flow.resilience.http.retry.max-attempts:2}") int maxAttempts,
			@Value("${bank-flow.resilience.http.retry.backoff-ms:500}") long backoffMs,
			@Value("${bank-flow.resilience.http.retry.multiplier:2.0}") double retryMultiplier,
			@Value("${bank-flow.resilience.http.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
			@Value("${bank-flow.resilience.http.circuit-breaker.sliding-window-size:10}") int slidingWindowSize,
			@Value("${bank-flow.resilience.http.circuit-breaker.minimum-calls:5}") int minimumCalls,
			@Value("${bank-flow.resilience.http.circuit-breaker.open-duration-ms:60000}") long openDurationMs
	) {
		this.retryConfig = RetryConfig.custom()
				.maxAttempts(Math.max(1, maxAttempts))
				.intervalFunction(IntervalFunction.ofExponentialBackoff(Math.max(1, backoffMs), retryMultiplier))
				.retryOnException(this::isRetryable)
				.build();
		this.circuitBreakerConfig = CircuitBreakerConfig.custom()
				.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
				.slidingWindowSize(Math.max(2, slidingWindowSize))
				.minimumNumberOfCalls(Math.max(1, minimumCalls))
				.failureRateThreshold(failureRateThreshold)
				.waitDurationInOpenState(Duration.ofMillis(Math.max(100, openDurationMs)))
				.permittedNumberOfCallsInHalfOpenState(1)
				.recordException(this::isRetryable)
				.build();
	}

	public <T> T execute(String operation, Supplier<T> supplier) {
		Retry retry = retries.computeIfAbsent(operation, name -> Retry.of(name, retryConfig));
		CircuitBreaker circuitBreaker = circuitBreakers.computeIfAbsent(
				operation,
				name -> CircuitBreaker.of(name, circuitBreakerConfig)
		);
		Supplier<T> retried = Retry.decorateSupplier(retry, supplier);
		return CircuitBreaker.decorateSupplier(circuitBreaker, retried).get();
	}

	private boolean isRetryable(Throwable exception) {
		if (exception instanceof ResourceAccessException) {
			return true;
		}
		if (exception instanceof RestClientResponseException responseException) {
			int status = responseException.getStatusCode().value();
			return status == 429 || responseException.getStatusCode().is5xxServerError();
		}
		return false;
	}
}

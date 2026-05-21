package br.com.bankflow.auth.worker.observability;

import br.com.bankflow.auth.kafka.AccountCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuthWorkerObservabilityAspect {

    private final ObservationRegistry observationRegistry;
    private final Counter accountCreatedConsumedCounter;
    private final Counter accountLinkedCounter;

    public AuthWorkerObservabilityAspect(
            ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.accountCreatedConsumedCounter =
                Counter.builder("auth.kafka.account_created.consumed")
                        .description("account.created events consumed by auth worker")
                        .register(meterRegistry);
        this.accountLinkedCounter =
                Counter.builder("auth.account_link.upserted")
                        .description("Account links upserted for authentication")
                        .register(meterRegistry);
    }

    @Around("execution(* br.com.bankflow.auth.worker.service.AccountLinkService.link(..))")
    public Object observeAccountLink(ProceedingJoinPoint joinPoint) throws Throwable {
        AccountCreatedEvent event = (AccountCreatedEvent) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted("auth.account.link", observationRegistry)
                        .contextualName("link account to credential document")
                        .highCardinalityKeyValue("account.id", event.accountId().toString());
        return observe(
                observation,
                joinPoint,
                ignored -> {
                    accountCreatedConsumedCounter.increment();
                    accountLinkedCounter.increment();
                });
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Object observe(
            Observation observation, ProceedingJoinPoint joinPoint, ObservationSuccess success)
            throws Throwable {
        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            Object result = joinPoint.proceed();
            success.accept(result);
            return result;
        } catch (Exception exception) {
            observation.error(exception);
            throw exception;
        } finally {
            observation.stop();
        }
    }

    @FunctionalInterface
    private interface ObservationSuccess {
        void accept(Object result);
    }
}

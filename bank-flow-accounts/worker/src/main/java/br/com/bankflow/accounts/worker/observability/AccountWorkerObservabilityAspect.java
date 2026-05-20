package br.com.bankflow.accounts.worker.observability;

import br.com.bankflow.accounts.shared.kafka.AccountRequestedEvent;
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
public class AccountWorkerObservabilityAspect {

    private final ObservationRegistry observationRegistry;
    private final Counter accountRequestedConsumedCounter;
    private final Counter accountPendingBaasCounter;
    private final Counter baasRequestCounter;

    public AccountWorkerObservabilityAspect(
            ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.accountRequestedConsumedCounter =
                Counter.builder("accounts.kafka.requested.consumed")
                        .description("account.requested events consumed by the worker")
                        .register(meterRegistry);
        this.accountPendingBaasCounter =
                Counter.builder("accounts.pending_baas")
                        .description("Accounts moved to PENDING_BAAS")
                        .register(meterRegistry);
        this.baasRequestCounter =
                Counter.builder("accounts.baas.creation.requests")
                        .description("Account creation requests sent to BaaS")
                        .register(meterRegistry);
    }

    @Around(
            "execution(* br.com.bankflow.accounts.worker.service.AccountRequestedService.process(..))")
    public Object observeAccountRequestedProcessing(ProceedingJoinPoint joinPoint)
            throws Throwable {
        AccountRequestedEvent event = (AccountRequestedEvent) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted("account.worker.process", observationRegistry)
                        .contextualName("process account requested")
                        .lowCardinalityKeyValue("account.status", "PENDING_BAAS")
                        .highCardinalityKeyValue("account.id", event.accountId().toString());

        return observe(
                observation,
                joinPoint,
                ignored -> {
                    accountRequestedConsumedCounter.increment();
                    accountPendingBaasCounter.increment();
                });
    }

    @Around(
            "execution(* br.com.bankflow.accounts.worker.client.BaasClient.requestAccountCreation(..))")
    public Object observeBaasAccountCreationRequest(ProceedingJoinPoint joinPoint)
            throws Throwable {
        AccountRequestedEvent event = (AccountRequestedEvent) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted("account.baas.request", observationRegistry)
                        .contextualName("request account creation on baas")
                        .highCardinalityKeyValue("account.id", event.accountId().toString());

        return observe(observation, joinPoint, ignored -> baasRequestCounter.increment());
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

package br.com.bankflow.balance.api.observability;

import br.com.bankflow.balance.shared.domain.Balance;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class BalanceApiObservabilityAspect {

    private final ObservationRegistry observationRegistry;
    private final Counter balanceLookupCounter;
    private final Counter balanceFoundCounter;
    private final Counter balanceEmptyCounter;

    public BalanceApiObservabilityAspect(
            ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.balanceLookupCounter =
                Counter.builder("balance.lookup.requested")
                        .description("Balance lookups requested by account id")
                        .register(meterRegistry);
        this.balanceFoundCounter =
                Counter.builder("balance.lookup.found")
                        .description("Balance lookups returning at least one currency")
                        .register(meterRegistry);
        this.balanceEmptyCounter =
                Counter.builder("balance.lookup.empty")
                        .description("Balance lookups returning no balances")
                        .register(meterRegistry);
    }

    @Around("execution(* br.com.bankflow.balance.api.service.BalanceService.findByAccountId(..))")
    public Object observeBalanceLookup(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID accountId = (UUID) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted("balance.lookup", observationRegistry)
                        .contextualName("lookup account balance")
                        .highCardinalityKeyValue("account.id", accountId.toString());

        return observe(
                observation,
                joinPoint,
                result -> {
                    balanceLookupCounter.increment();
                    if (result instanceof List<?> balances
                            && balances.stream().anyMatch(Balance.class::isInstance)) {
                        balanceFoundCounter.increment();
                    } else {
                        balanceEmptyCounter.increment();
                    }
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

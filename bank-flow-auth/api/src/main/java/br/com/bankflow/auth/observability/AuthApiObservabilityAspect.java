package br.com.bankflow.auth.observability;

import br.com.bankflow.auth.service.AuthException;
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
public class AuthApiObservabilityAspect {

    private final ObservationRegistry observationRegistry;
    private final Counter tokenIssuedCounter;
    private final Counter tokenRejectedCounter;
    private final Counter credentialsCreatedCounter;

    public AuthApiObservabilityAspect(
            ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.tokenIssuedCounter =
                Counter.builder("auth.token.issued")
                        .description("JWT tokens issued")
                        .register(meterRegistry);
        this.tokenRejectedCounter =
                Counter.builder("auth.token.rejected")
                        .description("JWT token requests rejected")
                        .register(meterRegistry);
        this.credentialsCreatedCounter =
                Counter.builder("auth.credentials.created")
                        .description("Credentials created")
                        .register(meterRegistry);
    }

    @Around("execution(* br.com.bankflow.auth.service.TokenService.create(..))")
    public Object observeTokenCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        Observation observation =
                Observation.createNotStarted("auth.token.create", observationRegistry)
                        .contextualName("create auth token");
        return observe(
                observation,
                joinPoint,
                ignored -> tokenIssuedCounter.increment(),
                exception -> tokenRejectedCounter.increment());
    }

    @Around("execution(* br.com.bankflow.auth.service.CredentialService.create(..))")
    public Object observeCredentialCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        Observation observation =
                Observation.createNotStarted("auth.credentials.create", observationRegistry)
                        .contextualName("create auth credentials");
        return observe(observation, joinPoint, ignored -> credentialsCreatedCounter.increment());
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Object observe(
            Observation observation, ProceedingJoinPoint joinPoint, ObservationSuccess success)
            throws Throwable {
        return observe(observation, joinPoint, success, ignored -> {});
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Object observe(
            Observation observation,
            ProceedingJoinPoint joinPoint,
            ObservationSuccess success,
            ObservationFailure failure)
            throws Throwable {
        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            Object result = joinPoint.proceed();
            success.accept(result);
            return result;
        } catch (AuthException exception) {
            failure.accept(exception);
            observation.error(exception);
            throw exception;
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

    @FunctionalInterface
    private interface ObservationFailure {
        void accept(Exception exception);
    }
}

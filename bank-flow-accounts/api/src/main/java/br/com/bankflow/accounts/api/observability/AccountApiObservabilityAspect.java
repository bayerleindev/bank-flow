package br.com.bankflow.accounts.api.observability;

import br.com.bankflow.accounts.api.service.BaasWebhookCommand;
import br.com.bankflow.accounts.shared.domain.Account;
import br.com.bankflow.accounts.shared.domain.AccountStatus;
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
public class AccountApiObservabilityAspect {

    private final ObservationRegistry observationRegistry;
    private final Counter accountRequestedCounter;
    private final Counter accountActivatedCounter;
    private final Counter accountRejectedCounter;
    private final Counter accountRequestedPublishedCounter;
    private final Counter accountCreatedPublishedCounter;

    public AccountApiObservabilityAspect(
            ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.accountRequestedCounter =
                Counter.builder("accounts.creation.requested")
                        .description("Accounts creation requests accepted by the API")
                        .register(meterRegistry);
        this.accountActivatedCounter =
                Counter.builder("accounts.webhook.active")
                        .description("Accounts activated by BaaS webhook")
                        .register(meterRegistry);
        this.accountRejectedCounter =
                Counter.builder("accounts.webhook.rejected")
                        .description("Accounts rejected by BaaS webhook")
                        .register(meterRegistry);
        this.accountRequestedPublishedCounter =
                Counter.builder("accounts.kafka.requested.published")
                        .description("account.requested events published")
                        .register(meterRegistry);
        this.accountCreatedPublishedCounter =
                Counter.builder("accounts.kafka.created.published")
                        .description("account.created events published")
                        .register(meterRegistry);
    }

    @Around("execution(* br.com.bankflow.accounts.api.service.AccountService.create(..))")
    public Object observeAccountCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        Observation observation =
                Observation.createNotStarted("account.create", observationRegistry)
                        .contextualName("request account creation")
                        .lowCardinalityKeyValue(
                                "account.status", AccountStatus.CREATION_REQUESTED.name());

        return observe(
                observation,
                joinPoint,
                result -> {
                    if (result instanceof Account account) {
                        observation.highCardinalityKeyValue("account.id", account.id().toString());
                        accountRequestedCounter.increment();
                    }
                });
    }

    @Around("execution(* br.com.bankflow.accounts.api.service.AccountService.activate(..))")
    public Object observeWebhookActivation(ProceedingJoinPoint joinPoint) throws Throwable {
        BaasWebhookCommand command = (BaasWebhookCommand) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted("account.webhook.activate", observationRegistry)
                        .contextualName("process baas account webhook")
                        .lowCardinalityKeyValue("account.status", command.status().name())
                        .highCardinalityKeyValue("account.id", command.accountId().toString());

        return observe(
                observation,
                joinPoint,
                ignored -> {
                    if (command.status() == AccountStatus.ACTIVE) {
                        accountActivatedCounter.increment();
                    } else if (command.status() == AccountStatus.REJECTED) {
                        accountRejectedCounter.increment();
                    }
                });
    }

    @Around(
            "execution(* br.com.bankflow.accounts.api.producer.AccountEventProducer.publishRequested(..))")
    public Object observeAccountRequestedPublish(ProceedingJoinPoint joinPoint) throws Throwable {
        Account account = (Account) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted("account.kafka.publish.requested", observationRegistry)
                        .contextualName("publish account requested")
                        .lowCardinalityKeyValue("messaging.destination", "account.requested")
                        .highCardinalityKeyValue("account.id", account.id().toString());

        return observe(
                observation, joinPoint, ignored -> accountRequestedPublishedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.accounts.api.producer.AccountEventProducer.publishCreated(..))")
    public Object observeAccountCreatedPublish(ProceedingJoinPoint joinPoint) throws Throwable {
        Account account = (Account) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted("account.kafka.publish.created", observationRegistry)
                        .contextualName("publish account created")
                        .lowCardinalityKeyValue("messaging.destination", "account.created")
                        .highCardinalityKeyValue("account.id", account.id().toString());

        return observe(
                observation, joinPoint, ignored -> accountCreatedPublishedCounter.increment());
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

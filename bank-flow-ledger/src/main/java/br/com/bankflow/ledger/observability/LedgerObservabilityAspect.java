package br.com.bankflow.ledger.observability;

import br.com.bankflow.ledger.domain.Journal;
import br.com.bankflow.ledger.domain.LedgerAccount;
import br.com.bankflow.ledger.kafka.AccountCreatedEvent;
import br.com.bankflow.ledger.kafka.MovementCreatedEvent;
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
public class LedgerObservabilityAspect {

    private final ObservationRegistry observationRegistry;
    private final Counter bootstrapCounter;
    private final Counter accountCreatedConsumedCounter;
    private final Counter accountRepresentationSavedCounter;
    private final Counter movementCreatedConsumedCounter;
    private final Counter journalSavedCounter;
    private final Counter journalCreatedPublishedCounter;

    public LedgerObservabilityAspect(
            ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.bootstrapCounter =
                Counter.builder("ledger.bootstrap.completed")
                        .description("Ledger bootstrap executions completed")
                        .register(meterRegistry);
        this.accountCreatedConsumedCounter =
                Counter.builder("ledger.kafka.account_created.consumed")
                        .description("account.created events consumed by ledger")
                        .register(meterRegistry);
        this.accountRepresentationSavedCounter =
                Counter.builder("ledger.account_representation.saved")
                        .description("Ledger account representations saved")
                        .register(meterRegistry);
        this.movementCreatedConsumedCounter =
                Counter.builder("ledger.kafka.movement_created.consumed")
                        .description("movement.created events consumed by ledger")
                        .register(meterRegistry);
        this.journalSavedCounter =
                Counter.builder("ledger.journal.saved")
                        .description("Ledger journals saved")
                        .register(meterRegistry);
        this.journalCreatedPublishedCounter =
                Counter.builder("ledger.kafka.journal_created.published")
                        .description("ledger.journal.created events published by ledger")
                        .register(meterRegistry);
    }

    @Around("execution(* br.com.bankflow.ledger.service.LedgerBootstrapService.bootstrap(..))")
    public Object observeBootstrap(ProceedingJoinPoint joinPoint) throws Throwable {
        Observation observation =
                Observation.createNotStarted("ledger.bootstrap", observationRegistry)
                        .contextualName("bootstrap ledger model");

        return observe(observation, joinPoint, ignored -> bootstrapCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.ledger.service.LedgerAccountService.createAccountRepresentation(..))")
    public Object observeAccountCreatedProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        AccountCreatedEvent event = (AccountCreatedEvent) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "ledger.worker.process.account_created", observationRegistry)
                        .contextualName("process account created")
                        .highCardinalityKeyValue("account.id", event.accountId().toString());

        return observe(
                observation, joinPoint, ignored -> accountCreatedConsumedCounter.increment());
    }

    @Around("execution(* br.com.bankflow.ledger.repository.LedgerRepository.saveAccount(..))")
    public Object observeAccountSave(ProceedingJoinPoint joinPoint) throws Throwable {
        LedgerAccount account = (LedgerAccount) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted("ledger.account.save", observationRegistry)
                        .contextualName("save ledger account representation")
                        .lowCardinalityKeyValue("ledger.account.type", account.type().name())
                        .highCardinalityKeyValue("account.id", account.accountId().toString());

        return observe(
                observation, joinPoint, ignored -> accountRepresentationSavedCounter.increment());
    }

    @Around("execution(* br.com.bankflow.ledger.service.LedgerMovementService.createJournal(..))")
    public Object observeMovementCreatedProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        MovementCreatedEvent event = (MovementCreatedEvent) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "ledger.worker.process.movement_created", observationRegistry)
                        .contextualName("process movement created")
                        .lowCardinalityKeyValue("movement.type", event.type())
                        .highCardinalityKeyValue("movement.id", event.movementId().toString())
                        .highCardinalityKeyValue("transfer.id", event.transferId().toString())
                        .highCardinalityKeyValue(
                                "debit.account.id", event.debitAccountId().toString())
                        .highCardinalityKeyValue(
                                "credit.account.id", event.creditAccountId().toString());

        return observe(
                observation, joinPoint, ignored -> movementCreatedConsumedCounter.increment());
    }

    @Around("execution(* br.com.bankflow.ledger.repository.LedgerRepository.saveJournal(..))")
    public Object observeJournalSave(ProceedingJoinPoint joinPoint) throws Throwable {
        Journal journal = (Journal) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted("ledger.journal.save", observationRegistry)
                        .contextualName("save ledger journal")
                        .lowCardinalityKeyValue("journal.type", journal.type())
                        .highCardinalityKeyValue("movement.id", journal.movementId().toString())
                        .highCardinalityKeyValue("transfer.id", journal.transferId().toString());

        return observe(observation, joinPoint, ignored -> journalSavedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.ledger.producer.LedgerEventProducer.publishJournalCreated(..))")
    public Object observeJournalCreatedPublish(ProceedingJoinPoint joinPoint) throws Throwable {
        Journal journal = (Journal) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "ledger.kafka.publish.journal_created", observationRegistry)
                        .contextualName("publish ledger journal created")
                        .lowCardinalityKeyValue("messaging.destination", "ledger.journal.created")
                        .highCardinalityKeyValue("movement.id", journal.movementId().toString())
                        .highCardinalityKeyValue("transfer.id", journal.transferId().toString());

        return observe(
                observation, joinPoint, ignored -> journalCreatedPublishedCounter.increment());
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

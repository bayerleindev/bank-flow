package br.com.bankflow.balance.worker.observability;

import br.com.bankflow.balance.shared.kafka.BalanceCaptureCommand;
import br.com.bankflow.balance.shared.kafka.BalanceHeldEvent;
import br.com.bankflow.balance.shared.kafka.BalanceHoldCommand;
import br.com.bankflow.balance.shared.kafka.BalanceReleaseCommand;
import br.com.bankflow.balance.shared.kafka.LedgerJournalCreatedEvent;
import br.com.bankflow.balance.shared.kafka.LedgerJournalEntryCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class BalanceWorkerObservabilityAspect {

    private final ObservationRegistry observationRegistry;
    private final Counter ledgerJournalConsumedCounter;
    private final Counter ledgerJournalEntryAppliedCounter;
    private final Counter ledgerJournalEntryIgnoredCounter;
    private final Counter balanceHoldConsumedCounter;
    private final Counter balanceHeldPublishedCounter;
    private final Counter balanceHeldApprovedCounter;
    private final Counter balanceHeldRejectedCounter;
    private final Counter balanceCaptureConsumedCounter;
    private final Counter balanceReleaseConsumedCounter;

    public BalanceWorkerObservabilityAspect(
            ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.ledgerJournalConsumedCounter =
                Counter.builder("balance.kafka.ledger_journal.consumed")
                        .description("ledger.journal.created events consumed by balance worker")
                        .register(meterRegistry);
        this.ledgerJournalEntryAppliedCounter =
                Counter.builder("balance.ledger_journal_entry.applied")
                        .description("Ledger journal entries applied to balance projection")
                        .register(meterRegistry);
        this.ledgerJournalEntryIgnoredCounter =
                Counter.builder("balance.ledger_journal_entry.ignored")
                        .description("Ledger journal entries ignored by balance projection")
                        .register(meterRegistry);
        this.balanceHoldConsumedCounter =
                Counter.builder("balance.kafka.hold_command.consumed")
                        .description("balance.hold.command messages consumed by balance worker")
                        .register(meterRegistry);
        this.balanceHeldPublishedCounter =
                Counter.builder("balance.kafka.held_event.published")
                        .description("balance.held.event messages published by balance worker")
                        .register(meterRegistry);
        this.balanceHeldApprovedCounter =
                Counter.builder("balance.hold.approved")
                        .description("Balance holds approved")
                        .register(meterRegistry);
        this.balanceHeldRejectedCounter =
                Counter.builder("balance.hold.rejected")
                        .description("Balance holds rejected")
                        .register(meterRegistry);
        this.balanceCaptureConsumedCounter =
                Counter.builder("balance.kafka.capture_command.consumed")
                        .description("balance.capture.command messages consumed by balance worker")
                        .register(meterRegistry);
        this.balanceReleaseConsumedCounter =
                Counter.builder("balance.kafka.release_command.consumed")
                        .description("balance.release.command messages consumed by balance worker")
                        .register(meterRegistry);
    }

    @Around(
            "execution(* br.com.bankflow.balance.worker.service.BalanceProjectionService.applyJournal(..))")
    public Object observeLedgerJournal(ProceedingJoinPoint joinPoint) throws Throwable {
        LedgerJournalCreatedEvent event = (LedgerJournalCreatedEvent) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "balance.worker.process.ledger_journal", observationRegistry)
                        .contextualName("process ledger journal")
                        .lowCardinalityKeyValue("journal.type", event.type())
                        .highCardinalityKeyValue("movement.id", event.movementId().toString())
                        .highCardinalityKeyValue("transfer.id", event.transferId().toString());

        return observe(observation, joinPoint, ignored -> ledgerJournalConsumedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.balance.shared.repository.BalanceRepository.applyEntry(..))")
    public Object observeLedgerJournalEntry(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID transferId = (UUID) joinPoint.getArgs()[0];
        LedgerJournalEntryCreatedEvent entry =
                (LedgerJournalEntryCreatedEvent) joinPoint.getArgs()[1];
        Observation observation =
                Observation.createNotStarted(
                                "balance.worker.apply.ledger_journal_entry", observationRegistry)
                        .contextualName("apply ledger journal entry to balance")
                        .lowCardinalityKeyValue("journal.side", entry.side().name())
                        .highCardinalityKeyValue("transfer.id", transferId.toString())
                        .highCardinalityKeyValue("movement.id", entry.movementId().toString())
                        .highCardinalityKeyValue("account.id", entry.accountId().toString());

        return observe(
                observation,
                joinPoint,
                result -> {
                    if (Boolean.TRUE.equals(result)) {
                        ledgerJournalEntryAppliedCounter.increment();
                    } else {
                        ledgerJournalEntryIgnoredCounter.increment();
                    }
                });
    }

    @Around("execution(* br.com.bankflow.balance.worker.service.BalanceHoldService.hold(..))")
    public Object observeBalanceHold(ProceedingJoinPoint joinPoint) throws Throwable {
        BalanceHoldCommand command = (BalanceHoldCommand) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "balance.worker.process.hold_command", observationRegistry)
                        .contextualName("process balance hold command")
                        .highCardinalityKeyValue("transfer.id", command.transferId().toString())
                        .highCardinalityKeyValue("account.id", command.debitAccountId().toString());

        return observe(observation, joinPoint, ignored -> balanceHoldConsumedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.balance.worker.producer.BalanceHeldEventProducer.publish(..))")
    public Object observeBalanceHeldPublish(ProceedingJoinPoint joinPoint) throws Throwable {
        BalanceHeldEvent event = (BalanceHeldEvent) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "balance.kafka.publish.held_event", observationRegistry)
                        .contextualName("publish balance held event")
                        .lowCardinalityKeyValue("balance.hold.status", event.status().name())
                        .highCardinalityKeyValue("transfer.id", event.transferId().toString())
                        .highCardinalityKeyValue("account.id", event.accountId().toString());

        return observe(
                observation,
                joinPoint,
                ignored -> {
                    balanceHeldPublishedCounter.increment();
                    switch (event.status()) {
                        case HELD -> balanceHeldApprovedCounter.increment();
                        case REJECTED -> balanceHeldRejectedCounter.increment();
                    }
                });
    }

    @Around(
            "execution(* br.com.bankflow.balance.worker.service.BalanceSettlementService.capture(..))")
    public Object observeBalanceCapture(ProceedingJoinPoint joinPoint) throws Throwable {
        BalanceCaptureCommand command = (BalanceCaptureCommand) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "balance.worker.process.capture_command", observationRegistry)
                        .contextualName("process balance capture command")
                        .highCardinalityKeyValue("transfer.id", command.transferId().toString())
                        .highCardinalityKeyValue("account.id", command.accountId().toString());

        return observe(
                observation, joinPoint, ignored -> balanceCaptureConsumedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.balance.worker.service.BalanceSettlementService.release(..))")
    public Object observeBalanceRelease(ProceedingJoinPoint joinPoint) throws Throwable {
        BalanceReleaseCommand command = (BalanceReleaseCommand) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "balance.worker.process.release_command", observationRegistry)
                        .contextualName("process balance release command")
                        .highCardinalityKeyValue("transfer.id", command.transferId().toString())
                        .highCardinalityKeyValue("account.id", command.accountId().toString());

        return observe(
                observation, joinPoint, ignored -> balanceReleaseConsumedCounter.increment());
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

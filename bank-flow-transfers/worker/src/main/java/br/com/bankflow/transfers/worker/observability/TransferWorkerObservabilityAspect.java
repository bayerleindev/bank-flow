package br.com.bankflow.transfers.worker.observability;

import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.kafka.AccountValidateCommand;
import br.com.bankflow.transfers.shared.kafka.AccountValidatedEvent;
import br.com.bankflow.transfers.shared.kafka.BalanceCaptureCommand;
import br.com.bankflow.transfers.shared.kafka.BalanceHeldEvent;
import br.com.bankflow.transfers.shared.kafka.BalanceHoldCommand;
import br.com.bankflow.transfers.shared.kafka.TransferRequestedEvent;
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
public class TransferWorkerObservabilityAspect {

    private final ObservationRegistry observationRegistry;
    private final Counter transferRequestedConsumedCounter;
    private final Counter accountValidatedConsumedCounter;
    private final Counter balanceHeldConsumedCounter;
    private final Counter accountValidatePublishedCounter;
    private final Counter balanceHoldPublishedCounter;
    private final Counter balanceCapturePublishedCounter;
    private final Counter movementCreatedPublishedCounter;
    private final Counter baasPixPaymentRequestedCounter;

    public TransferWorkerObservabilityAspect(
            ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.transferRequestedConsumedCounter =
                Counter.builder("transfers.kafka.requested.consumed")
                        .description("transfer.requested events consumed by transfers worker")
                        .register(meterRegistry);
        this.accountValidatedConsumedCounter =
                Counter.builder("transfers.kafka.account_validated.consumed")
                        .description("account.validated.event events consumed by transfers worker")
                        .register(meterRegistry);
        this.balanceHeldConsumedCounter =
                Counter.builder("transfers.kafka.balance_held.consumed")
                        .description("balance.held.event events consumed by transfers worker")
                        .register(meterRegistry);
        this.accountValidatePublishedCounter =
                Counter.builder("transfers.kafka.account_validate.published")
                        .description("account.validate.command messages published by transfers")
                        .register(meterRegistry);
        this.balanceHoldPublishedCounter =
                Counter.builder("transfers.kafka.balance_hold.published")
                        .description("balance.hold.command messages published by transfers")
                        .register(meterRegistry);
        this.balanceCapturePublishedCounter =
                Counter.builder("transfers.kafka.balance_capture.published")
                        .description("balance.capture.command messages published by transfers")
                        .register(meterRegistry);
        this.movementCreatedPublishedCounter =
                Counter.builder("transfers.kafka.movement_created.published")
                        .description("movement.created events published by transfers worker")
                        .register(meterRegistry);
        this.baasPixPaymentRequestedCounter =
                Counter.builder("transfers.baas.pix_payment.requests")
                        .description("Pix payment requests sent to BaaS")
                        .register(meterRegistry);
    }

    @Around(
            "execution(* br.com.bankflow.transfers.worker.service.TransferOrchestratorService.startProcessing(..))")
    public Object observeTransferRequestedProcessing(ProceedingJoinPoint joinPoint)
            throws Throwable {
        TransferRequestedEvent event = (TransferRequestedEvent) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "transfer.worker.process.requested", observationRegistry)
                        .contextualName("process transfer requested")
                        .lowCardinalityKeyValue("transfer.type", event.type().name())
                        .lowCardinalityKeyValue("transfer.status", event.status())
                        .highCardinalityKeyValue("transfer.id", event.id().toString());

        return observe(
                observation, joinPoint, ignored -> transferRequestedConsumedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.transfers.worker.service.TransferOrchestratorService.handleAccountValidated(..))")
    public Object observeAccountValidatedProcessing(ProceedingJoinPoint joinPoint)
            throws Throwable {
        AccountValidatedEvent event = (AccountValidatedEvent) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "transfer.worker.process.account_validated", observationRegistry)
                        .contextualName("process account validated")
                        .lowCardinalityKeyValue("account.validation.status", event.status().name())
                        .highCardinalityKeyValue("transfer.id", event.transferId().toString());

        return observe(
                observation, joinPoint, ignored -> accountValidatedConsumedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.transfers.worker.service.TransferOrchestratorService.handleBalanceHeld(..))")
    public Object observeBalanceHeldProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        BalanceHeldEvent event = (BalanceHeldEvent) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "transfer.worker.process.balance_held", observationRegistry)
                        .contextualName("process balance held")
                        .lowCardinalityKeyValue("balance.hold.status", event.status().name())
                        .highCardinalityKeyValue("transfer.id", event.transferId().toString());

        return observe(observation, joinPoint, ignored -> balanceHeldConsumedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.transfers.worker.producer.TransferCommandProducer.publishAccountValidate(..))")
    public Object observeAccountValidatePublish(ProceedingJoinPoint joinPoint) throws Throwable {
        AccountValidateCommand command = (AccountValidateCommand) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "transfer.kafka.publish.account_validate", observationRegistry)
                        .contextualName("publish account validate")
                        .lowCardinalityKeyValue("messaging.destination", "account.validate.command")
                        .highCardinalityKeyValue("transfer.id", command.transferId().toString());

        return observe(
                observation, joinPoint, ignored -> accountValidatePublishedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.transfers.worker.producer.TransferCommandProducer.publishBalanceHold(..))")
    public Object observeBalanceHoldPublish(ProceedingJoinPoint joinPoint) throws Throwable {
        BalanceHoldCommand command = (BalanceHoldCommand) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "transfer.kafka.publish.balance_hold", observationRegistry)
                        .contextualName("publish balance hold")
                        .lowCardinalityKeyValue("messaging.destination", "balance.hold.command")
                        .highCardinalityKeyValue("transfer.id", command.transferId().toString());

        return observe(observation, joinPoint, ignored -> balanceHoldPublishedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.transfers.worker.producer.TransferCommandProducer.publishBalanceCapture(..))")
    public Object observeBalanceCapturePublish(ProceedingJoinPoint joinPoint) throws Throwable {
        BalanceCaptureCommand command = (BalanceCaptureCommand) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "transfer.kafka.publish.balance_capture", observationRegistry)
                        .contextualName("publish balance capture")
                        .lowCardinalityKeyValue("messaging.destination", "balance.capture.command")
                        .highCardinalityKeyValue("transfer.id", command.transferId().toString());

        return observe(
                observation, joinPoint, ignored -> balanceCapturePublishedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.transfers.worker.producer.MovementEventProducer.publishCreated(..))")
    public Object observeMovementCreatedPublish(ProceedingJoinPoint joinPoint) throws Throwable {
        Transfer transfer = (Transfer) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "transfer.kafka.publish.movement_created", observationRegistry)
                        .contextualName("publish movement created")
                        .lowCardinalityKeyValue("messaging.destination", "movement.created")
                        .highCardinalityKeyValue("transfer.id", transfer.id().toString());

        return observe(
                observation, joinPoint, ignored -> movementCreatedPublishedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.transfers.worker.client.BaasTransferClient.requestPixPayment(..))")
    public Object observeBaasPixPaymentRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        Transfer transfer = (Transfer) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "transfer.baas.pix_payment.request", observationRegistry)
                        .contextualName("request pix payment on baas")
                        .highCardinalityKeyValue("transfer.id", transfer.id().toString());

        return observe(
                observation, joinPoint, ignored -> baasPixPaymentRequestedCounter.increment());
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

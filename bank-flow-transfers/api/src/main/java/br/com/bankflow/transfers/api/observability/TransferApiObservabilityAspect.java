package br.com.bankflow.transfers.api.observability;

import br.com.bankflow.transfers.api.dto.request.BaasTransferWebhookRequest;
import br.com.bankflow.transfers.api.dto.request.BaasTransferWebhookStatus;
import br.com.bankflow.transfers.api.service.CreateTransferCommand;
import br.com.bankflow.transfers.shared.domain.Transfer;
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
public class TransferApiObservabilityAspect {

    private final ObservationRegistry observationRegistry;
    private final Counter transferRequestedCounter;
    private final Counter transferWebhookCompletedCounter;
    private final Counter transferWebhookRejectedCounter;
    private final Counter transferRequestedPublishedCounter;
    private final Counter balanceCapturePublishedCounter;
    private final Counter balanceReleasePublishedCounter;
    private final Counter movementCreatedPublishedCounter;
    private final Counter baasDictLookupCounter;

    public TransferApiObservabilityAspect(
            ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.transferRequestedCounter =
                Counter.builder("transfers.creation.requested")
                        .description("Transfer creation requests accepted by the API")
                        .register(meterRegistry);
        this.transferWebhookCompletedCounter =
                Counter.builder("transfers.webhook.completed")
                        .description("External transfers completed by BaaS webhook")
                        .register(meterRegistry);
        this.transferWebhookRejectedCounter =
                Counter.builder("transfers.webhook.rejected")
                        .description("External transfers rejected by BaaS webhook")
                        .register(meterRegistry);
        this.transferRequestedPublishedCounter =
                Counter.builder("transfers.kafka.requested.published")
                        .description("transfer.requested events published")
                        .register(meterRegistry);
        this.balanceCapturePublishedCounter =
                Counter.builder("transfers.kafka.balance_capture.published")
                        .description("balance.capture.command messages published by transfers")
                        .register(meterRegistry);
        this.balanceReleasePublishedCounter =
                Counter.builder("transfers.kafka.balance_release.published")
                        .description("balance.release.command messages published by transfers")
                        .register(meterRegistry);
        this.movementCreatedPublishedCounter =
                Counter.builder("transfers.kafka.movement_created.published")
                        .description("movement.created events published by transfers")
                        .register(meterRegistry);
        this.baasDictLookupCounter =
                Counter.builder("transfers.baas.dict.lookup")
                        .description("Pix DICT lookups sent to BaaS")
                        .register(meterRegistry);
    }

    @Around("execution(* br.com.bankflow.transfers.api.service.TransferService.create(..))")
    public Object observeTransferCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        CreateTransferCommand command = (CreateTransferCommand) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted("transfer.create", observationRegistry)
                        .contextualName("request transfer creation")
                        .lowCardinalityKeyValue("transfer.type", command.type().name())
                        .lowCardinalityKeyValue(
                                "transfer.requested_status", command.type().requestedStatus());

        return observe(
                observation,
                joinPoint,
                result -> {
                    if (result instanceof Transfer transfer) {
                        observation.highCardinalityKeyValue(
                                "transfer.id", transfer.id().toString());
                        transferRequestedCounter.increment();
                    }
                });
    }

    @Around(
            "execution(* br.com.bankflow.transfers.api.service.BaasTransferWebhookService.handle(..))")
    public Object observeBaasWebhook(ProceedingJoinPoint joinPoint) throws Throwable {
        BaasTransferWebhookRequest request = (BaasTransferWebhookRequest) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted("transfer.webhook.baas", observationRegistry)
                        .contextualName("process baas transfer webhook")
                        .lowCardinalityKeyValue("transfer.status", request.status().name())
                        .highCardinalityKeyValue("transfer.id", request.transferId().toString());

        return observe(
                observation,
                joinPoint,
                ignored -> {
                    if (request.status() == BaasTransferWebhookStatus.COMPLETED) {
                        transferWebhookCompletedCounter.increment();
                    } else if (request.status() == BaasTransferWebhookStatus.REJECTED) {
                        transferWebhookRejectedCounter.increment();
                    }
                });
    }

    @Around(
            "execution(* br.com.bankflow.transfers.api.producer.TransferEventProducer.publishRequested(..))")
    public Object observeTransferRequestedPublish(ProceedingJoinPoint joinPoint) throws Throwable {
        Transfer transfer = (Transfer) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "transfer.kafka.publish.requested", observationRegistry)
                        .contextualName("publish transfer requested")
                        .lowCardinalityKeyValue("messaging.destination", "transfer.requested")
                        .highCardinalityKeyValue("transfer.id", transfer.id().toString());

        return observe(
                observation, joinPoint, ignored -> transferRequestedPublishedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.transfers.api.producer.TransferSettlementProducer.publishSuccessfulSettlement(..))")
    public Object observeSuccessfulSettlementPublish(ProceedingJoinPoint joinPoint)
            throws Throwable {
        Transfer transfer = (Transfer) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "transfer.kafka.publish.settlement.success", observationRegistry)
                        .contextualName("publish transfer successful settlement")
                        .lowCardinalityKeyValue("settlement.status", "COMPLETED")
                        .highCardinalityKeyValue("transfer.id", transfer.id().toString());

        return observe(
                observation,
                joinPoint,
                ignored -> {
                    movementCreatedPublishedCounter.increment();
                    balanceCapturePublishedCounter.increment();
                });
    }

    @Around(
            "execution(* br.com.bankflow.transfers.api.producer.TransferSettlementProducer.publishRejectedSettlement(..))")
    public Object observeRejectedSettlementPublish(ProceedingJoinPoint joinPoint) throws Throwable {
        Transfer transfer = (Transfer) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "transfer.kafka.publish.settlement.rejected", observationRegistry)
                        .contextualName("publish transfer rejected settlement")
                        .lowCardinalityKeyValue("settlement.status", "REJECTED")
                        .highCardinalityKeyValue("transfer.id", transfer.id().toString());

        return observe(
                observation, joinPoint, ignored -> balanceReleasePublishedCounter.increment());
    }

    @Around("execution(* br.com.bankflow.transfers.api.client.BaasDictClient.findByKey(..))")
    public Object observeBaasDictLookup(ProceedingJoinPoint joinPoint) throws Throwable {
        Observation observation =
                Observation.createNotStarted("transfer.baas.dict.lookup", observationRegistry)
                        .contextualName("lookup pix key on baas dict");

        return observe(observation, joinPoint, ignored -> baasDictLookupCounter.increment());
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

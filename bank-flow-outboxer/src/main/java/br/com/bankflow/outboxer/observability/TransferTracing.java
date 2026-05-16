package br.com.bankflow.outboxer.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static br.com.bankflow.outboxer.observability.TraceConstants.TRANSFER_ID;
import static br.com.bankflow.outboxer.observability.TraceConstants.TRANSFER_ID_SPAN_ATTR;

@Component
public class TransferTracing {

    private final Tracer tracer;
    private final ObservationRegistry observationRegistry;

    public TransferTracing(
            Tracer tracer,
            ObservationRegistry observationRegistry
    ) {
        this.tracer = tracer;
        this.observationRegistry = observationRegistry;
    }

    public void attachTransferId(UUID transferId) {
        if (transferId == null) {
            return;
        }

        String value = transferId.toString();

        Observation currentObservation = observationRegistry.getCurrentObservation();

        if (currentObservation != null) {
            currentObservation.highCardinalityKeyValue(TRANSFER_ID_SPAN_ATTR, value);
        }

        if (tracer.currentSpan() != null) {
            tracer.currentSpan().tag(TRANSFER_ID_SPAN_ATTR, value);
        }

        MDC.put(TRANSFER_ID, value);
    }

    public <T> T withTransferId(UUID transferId, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");

        if (transferId == null) {
            return supplier.get();
        }

        String value = transferId.toString();

        try (
                BaggageInScope baggage = tracer.createBaggageInScope(TRANSFER_ID, value);
                MDC.MDCCloseable ignored = MDC.putCloseable(TRANSFER_ID, value)
        ) {
            attachTransferId(transferId);
            return supplier.get();
        }
    }

    public void withTransferId(UUID transferId, Runnable runnable) {
        withTransferId(transferId, () -> {
            runnable.run();
            return null;
        });
    }

    public String currentTransferId() {
        var baggage = tracer.getBaggage(TRANSFER_ID);

        if (baggage != null && baggage.get() != null && !baggage.get().isBlank()) {
            return baggage.get();
        }

        return MDC.get(TRANSFER_ID);
    }
}

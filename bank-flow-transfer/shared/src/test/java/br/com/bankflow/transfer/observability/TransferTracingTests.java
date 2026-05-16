package br.com.bankflow.transfer.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;

import static br.com.bankflow.transfer.observability.TraceConstants.TRANSFER_ID;
import static br.com.bankflow.transfer.observability.TraceConstants.TRANSFER_ID_SPAN_ATTR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class TransferTracingTests {

    private Tracer tracer;
    private ObservationRegistry observationRegistry;
    private TransferTracing transferTracing;

    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        observationRegistry = mock(ObservationRegistry.class);
        transferTracing = new TransferTracing(tracer, observationRegistry);
        MDC.clear();
    }

    @Test
    void attachesTransferIdToMdcAndSpan() {
        UUID transferId = UUID.randomUUID();
        Span span = mock(Span.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(observationRegistry.getCurrentObservation()).thenReturn(null);

        transferTracing.attachTransferId(transferId);

        assertEquals(transferId.toString(), MDC.get(TRANSFER_ID));
        verify(span).tag(TRANSFER_ID_SPAN_ATTR, transferId.toString());
    }

    @Test
    void attachesTransferIdToObservationIfPresent() {
        UUID transferId = UUID.randomUUID();
        Observation observation = mock(Observation.class);
        when(observationRegistry.getCurrentObservation()).thenReturn(observation);
        when(tracer.currentSpan()).thenReturn(null);

        transferTracing.attachTransferId(transferId);

        verify(observation).highCardinalityKeyValue(TRANSFER_ID_SPAN_ATTR, transferId.toString());
    }

    @Test
    void withTransferIdRunsSupplierAndCleansUp() {
        UUID transferId = UUID.randomUUID();
        BaggageInScope baggage = mock(BaggageInScope.class);
        when(tracer.createBaggageInScope(TRANSFER_ID, transferId.toString())).thenReturn(baggage);

        String result = transferTracing.withTransferId(transferId, () -> {
            assertEquals(transferId.toString(), MDC.get(TRANSFER_ID));
            return "ok";
        });

        assertEquals("ok", result);
        assertNull(MDC.get(TRANSFER_ID));
        verify(baggage).close();
    }

    @Test
    void currentTransferIdReturnsFromBaggageFirst() {
        io.micrometer.tracing.Baggage baggage = mock(io.micrometer.tracing.Baggage.class);
        when(baggage.get()).thenReturn("from-baggage");
        when(tracer.getBaggage(TRANSFER_ID)).thenReturn(baggage);

        assertEquals("from-baggage", transferTracing.currentTransferId());
    }

    @Test
    void currentTransferIdReturnsFromMdcIfBaggageIsMissing() {
        when(tracer.getBaggage(TRANSFER_ID)).thenReturn(null);
        MDC.put(TRANSFER_ID, "from-mdc");

        assertEquals("from-mdc", transferTracing.currentTransferId());
    }
}

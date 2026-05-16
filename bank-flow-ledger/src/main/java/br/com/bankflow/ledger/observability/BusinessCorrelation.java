package br.com.bankflow.ledger.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BusinessCorrelation {
	private BusinessCorrelation() {
	}

	public static Scope transfer(Tracer tracer, UUID transferId, UUID sourceDigitalAccountId, UUID destinationDigitalAccountId) {
		Map<String, String> values = new LinkedHashMap<>();
		put(values, "transfer_id", transferId);
		put(values, "source_digital_account_id", sourceDigitalAccountId);
		put(values, "destination_digital_account_id", destinationDigitalAccountId);
		return apply(tracer, values);
	}

	public static Scope posting(Tracer tracer, String externalId, long entryId) {
		Map<String, String> values = new LinkedHashMap<>();
		put(values, "transfer_id", externalId);
		put(values, "external_id", externalId);
		put(values, "entry_id", entryId);
		return apply(tracer, values);
	}

	private static Scope apply(Tracer tracer, Map<String, String> values) {
		Span span = tracer == null ? null : tracer.currentSpan();
		if (span != null) {
			tag(span, "transfer.id", values.get("transfer_id"));
			tag(span, "ledger.external_id", values.get("external_id"));
			tag(span, "ledger.entry_id", values.get("entry_id"));
			tag(span, "source.digital_account_id", values.get("source_digital_account_id"));
			tag(span, "destination.digital_account_id", values.get("destination_digital_account_id"));
		}

		Map<String, String> previous = new LinkedHashMap<>();
		values.forEach((key, value) -> {
			previous.put(key, MDC.get(key));
			MDC.put(key, value);
		});
		return new Scope(previous);
	}

	private static void tag(Span span, String key, String value) {
		if (value != null && !value.isBlank()) {
			span.tag(key, value);
		}
	}

	private static void put(Map<String, String> values, String key, Object value) {
		if (value != null) {
			values.put(key, value.toString());
		}
	}

	public static final class Scope implements AutoCloseable {
		private final Map<String, String> previous;

		private Scope(Map<String, String> previous) {
			this.previous = previous;
		}

		@Override
		public void close() {
			previous.forEach((key, value) -> {
				if (value == null) {
					MDC.remove(key);
				} else {
					MDC.put(key, value);
				}
			});
		}
	}
}

package br.com.bankflow.balance.observability;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BusinessCorrelation {
	private BusinessCorrelation() {
	}

	public static Scope ledgerPosting(String externalId, long entryId) {
		Map<String, String> values = new LinkedHashMap<>();
		put(values, "transaction_id", externalId);
		put(values, "transfer_id", externalId);
		put(values, "external_id", externalId);
		put(values, "entry_id", entryId);
		return apply(values);
	}

	private static Scope apply(Map<String, String> values) {
		Map<String, String> previous = new LinkedHashMap<>();
		values.forEach((key, value) -> {
			previous.put(key, MDC.get(key));
			MDC.put(key, value);
		});
		return new Scope(previous);
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

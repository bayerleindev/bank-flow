package br.com.bankflow.ledger.observability;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;

public final class KafkaTraceContext {
	private static final ThreadLocal<TraceHeaders> CURRENT = new ThreadLocal<>();

	private KafkaTraceContext() {
	}

	public static void setFrom(Headers headers) {
		CURRENT.set(new TraceHeaders(headerValue(headers, "traceparent"), headerValue(headers, "tracestate")));
	}

	public static void clear() {
		CURRENT.remove();
	}

	public static String traceparent() {
		TraceHeaders headers = CURRENT.get();
		return headers == null ? null : headers.traceparent();
	}

	public static String tracestate() {
		TraceHeaders headers = CURRENT.get();
		return headers == null ? null : headers.tracestate();
	}

	private static String headerValue(Headers headers, String name) {
		Header header = headers == null ? null : headers.lastHeader(name);
		if (header == null || header.value() == null || header.value().length == 0) {
			return null;
		}
		String value = new String(header.value(), StandardCharsets.UTF_8);
		return value.isBlank() ? null : value;
	}

	private record TraceHeaders(String traceparent, String tracestate) {
	}
}

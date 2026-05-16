package br.com.bankflow.ledger.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class TransferTraceContext {
	private TransferTraceContext() {
	}

	public static String traceparent(String transferId, String spanSeed) {
		String traceId = traceId(transferId);
		if (traceId == null) {
			return null;
		}
		return "00-" + traceId + "-" + spanId(spanSeed == null ? transferId : spanSeed) + "-01";
	}

	private static String traceId(String transferId) {
		if (transferId == null || transferId.isBlank()) {
			return null;
		}
		try {
			String traceId = UUID.fromString(transferId).toString().replace("-", "");
			return isAllZero(traceId) ? null : traceId;
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static String spanId(String seed) {
		String spanId = sha256Hex(seed).substring(0, 16);
		return isAllZero(spanId) ? "0000000000000001" : spanId;
	}

	private static String sha256Hex(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	private static boolean isAllZero(String value) {
		return value.chars().allMatch(character -> character == '0');
	}
}

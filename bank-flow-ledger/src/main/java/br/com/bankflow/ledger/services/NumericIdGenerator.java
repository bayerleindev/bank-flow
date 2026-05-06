package br.com.bankflow.ledger.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class NumericIdGenerator {
	private static final long SEQUENCE_MODULO = 10_000L;
	private static final long ID_MULTIPLIER = 1_000_000L;
	private static final int MAX_WORKER_ID = 99;

	private final Clock clock;
	private final long workerOffset;
	private long lastTimestamp;
	private long sequence;

	@Autowired
	public NumericIdGenerator(Clock clock, @Value("${bank-flow.id-generator.worker-id:0}") int workerId) {
		if (workerId < 0 || workerId > MAX_WORKER_ID) {
			throw new IllegalArgumentException("worker_id must be between 0 and 99");
		}
		this.clock = clock;
		this.workerOffset = workerId * SEQUENCE_MODULO;
		this.lastTimestamp = -1L;
	}

	NumericIdGenerator(Clock clock) {
		this(clock, 0);
	}

	public long nextAccountId() {
		return nextId();
	}

	public long nextEntryId() {
		return nextId();
	}

	public long nextLineId() {
		return nextId();
	}

	private synchronized long nextId() {
		while (true) {
			long timestamp = Math.max(clock.millis(), lastTimestamp);
			if (timestamp == lastTimestamp) {
				if (sequence < SEQUENCE_MODULO - 1) {
					sequence++;
					return buildId(timestamp);
				}
				Thread.onSpinWait();
				continue;
			}
			lastTimestamp = timestamp;
			sequence = 0L;
			return buildId(timestamp);
		}
	}

	private long buildId(long timestamp) {
		return timestamp * ID_MULTIPLIER + workerOffset + sequence;
	}
}

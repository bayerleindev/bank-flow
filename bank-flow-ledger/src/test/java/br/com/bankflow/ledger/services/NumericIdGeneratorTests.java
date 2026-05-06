package br.com.bankflow.ledger.services;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericIdGeneratorTests {
	@Test
	void generatesUniqueMonotonicAccountIds() {
		NumericIdGenerator generator = new NumericIdGenerator(fixedClock());

		assertUniqueMonotonic(generator::nextAccountId);
	}

	@Test
	void generatesUniqueMonotonicEntryIds() {
		NumericIdGenerator generator = new NumericIdGenerator(fixedClock());

		assertUniqueMonotonic(generator::nextEntryId);
	}

	@Test
	void generatesUniqueMonotonicLineIds() {
		NumericIdGenerator generator = new NumericIdGenerator(fixedClock());

		assertUniqueMonotonic(generator::nextLineId);
	}

	@Test
	void generatesUniqueIdsAcrossIdTypes() {
		NumericIdGenerator generator = new NumericIdGenerator(fixedClock());

		long accountId = generator.nextAccountId();
		long entryId = generator.nextEntryId();
		long lineId = generator.nextLineId();

		assertTrue(accountId < entryId);
		assertTrue(entryId < lineId);
	}

	@Test
	void includesWorkerIdInGeneratedIds() {
		NumericIdGenerator firstWorker = new NumericIdGenerator(fixedClock(), 1);
		NumericIdGenerator secondWorker = new NumericIdGenerator(fixedClock(), 2);

		assertTrue(firstWorker.nextEntryId() < secondWorker.nextEntryId());
	}

	private Clock fixedClock() {
		return Clock.fixed(Instant.parse("2026-05-05T20:00:00Z"), ZoneOffset.UTC);
	}

	private void assertUniqueMonotonic(LongSupplier supplier) {
		Set<Long> ids = new HashSet<>();

		long previous = 0L;
		for (int index = 0; index < 2_000; index++) {
			long id = supplier.getAsLong();
			ids.add(id);

			assertTrue(id > previous);
			previous = id;
		}

		assertEquals(2_000, ids.size());
	}
}

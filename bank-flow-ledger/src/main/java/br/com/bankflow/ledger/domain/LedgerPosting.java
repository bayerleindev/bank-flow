package br.com.bankflow.ledger.domain;

import java.util.List;

public record LedgerPosting(
		LedgerEntry entry,
		List<LedgerEntryLine> lines
) {
	public static LedgerPosting of(LedgerEntry entry, List<LedgerEntryLine> lines) {
		if (entry == null) {
			throw new IllegalArgumentException("ledger entry is required");
		}
		if (lines == null || lines.size() < 2) {
			throw new IllegalArgumentException("ledger posting must have at least two lines");
		}

		List<LedgerEntryLine> immutableLines = List.copyOf(lines);
		String currency = immutableLines.getFirst().currency();
		long signedAmountTotal = 0L;

		for (LedgerEntryLine line : immutableLines) {
			if (line.entryId() != entry.entryId()) {
				throw new IllegalArgumentException("ledger entry line must reference posting entry_id");
			}
			if (!currency.equals(line.currency())) {
				throw new IllegalArgumentException("ledger posting lines must use the same currency");
			}
			if (!"DEBIT".equals(line.direction()) && !"CREDIT".equals(line.direction())) {
				throw new IllegalArgumentException("ledger entry line direction must be DEBIT or CREDIT");
			}
			if ("DEBIT".equals(line.direction()) && line.signedAmountMinor() >= 0) {
				throw new IllegalArgumentException("debit line signed_amount_minor must be negative");
			}
			if ("CREDIT".equals(line.direction()) && line.signedAmountMinor() <= 0) {
				throw new IllegalArgumentException("credit line signed_amount_minor must be positive");
			}
			if (Math.abs(line.signedAmountMinor()) != line.amountMinor()) {
				throw new IllegalArgumentException("line signed_amount_minor must match amount_minor");
			}
			signedAmountTotal += line.signedAmountMinor();
		}

		if (signedAmountTotal != 0L) {
			throw new IllegalArgumentException("ledger posting must be balanced");
		}

		return new LedgerPosting(entry, immutableLines);
	}
}

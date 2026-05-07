package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.LedgerEntry;
import br.com.bankflow.ledger.domain.LedgerEntryLine;
import br.com.bankflow.ledger.domain.LedgerPosting;
import br.com.bankflow.ledger.domain.LedgerReversalRequestedEvent;
import br.com.bankflow.ledger.observability.LedgerBusinessMetrics;
import br.com.bankflow.ledger.repositories.LedgerPostingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LedgerReversalService {
	private static final Logger log = LoggerFactory.getLogger(LedgerReversalService.class);

	private final LedgerPostingRepository ledgerPostingRepository;
	private final LedgerPostingPublisher ledgerPostingPublisher;
	private final NumericIdGenerator numericIdGenerator;
	private final Clock clock;
	private final ObjectMapper objectMapper;
	private final LedgerBusinessMetrics ledgerBusinessMetrics;

	public LedgerReversalService(
			LedgerPostingRepository ledgerPostingRepository,
			LedgerPostingPublisher ledgerPostingPublisher,
			NumericIdGenerator numericIdGenerator,
			Clock clock,
			ObjectMapper objectMapper
	) {
		this(
				ledgerPostingRepository,
				ledgerPostingPublisher,
				numericIdGenerator,
				clock,
				objectMapper,
				new LedgerBusinessMetrics(new SimpleMeterRegistry())
		);
	}

	@Autowired
	public LedgerReversalService(
			LedgerPostingRepository ledgerPostingRepository,
			LedgerPostingPublisher ledgerPostingPublisher,
			NumericIdGenerator numericIdGenerator,
			Clock clock,
			ObjectMapper objectMapper,
			LedgerBusinessMetrics ledgerBusinessMetrics
	) {
		this.ledgerPostingRepository = ledgerPostingRepository;
		this.ledgerPostingPublisher = ledgerPostingPublisher;
		this.numericIdGenerator = numericIdGenerator;
		this.clock = clock;
		this.objectMapper = objectMapper;
		this.ledgerBusinessMetrics = ledgerBusinessMetrics;
	}

	public void reverse(LedgerReversalRequestedEvent event) throws JsonProcessingException {
		try {
			event.validate();
		} catch (IllegalArgumentException exception) {
			ledgerBusinessMetrics.recordValidationFailure("reverse", exception.getMessage());
			throw exception;
		}
		String reversalExternalId = event.reversalId().toString();
		if (ledgerPostingRepository.findByExternalId(reversalExternalId).isPresent()) {
			ledgerBusinessMetrics.recordIdempotencyHit("reverse");
			log.info("ledger reversal already processed reversalExternalId={}", reversalExternalId);
			return;
		}

		LedgerPosting originalPosting = ledgerPostingRepository.findByExternalId(event.originalExternalId())
				.orElseThrow(() -> {
					String message = "original ledger posting not found original_external_id=%s".formatted(event.originalExternalId());
					ledgerBusinessMetrics.recordValidationFailure("reverse", message);
					return new IllegalArgumentException(message);
				});
		LedgerEntry originalEntry = originalPosting.entry();
		if ("REVERSAL".equals(originalEntry.entryType())) {
			ledgerBusinessMetrics.recordValidationFailure("reverse", "reversal entries cannot be reversed");
			throw new IllegalArgumentException("reversal entries cannot be reversed");
		}
		if (ledgerPostingRepository.reversalExistsFor(originalEntry.entryId())) {
			ledgerBusinessMetrics.recordValidationFailure("reverse", "original ledger posting is already reversed");
			throw new IllegalArgumentException("original ledger posting is already reversed");
		}

		long now = clock.millis();
		LedgerEntry reversalEntry = LedgerEntry.reversal(
				numericIdGenerator.nextEntryId(),
				now,
				now,
				originalEntry.entryId(),
				reversalExternalId,
				"Estorno do lancamento %s".formatted(originalEntry.externalId()),
				buildMetadata(event, originalEntry)
		);
		List<LedgerEntryLine> reversalLines = new ArrayList<>();
		for (LedgerEntryLine originalLine : originalPosting.lines()) {
			reversalLines.add(LedgerEntryLine.reversalOf(
					numericIdGenerator.nextLineId(),
					reversalEntry.entryId(),
					originalLine,
					"Estorno da linha %d".formatted(originalLine.lineId()),
					now
			));
		}

		LedgerPosting reversalPosting = createPosting(reversalEntry, reversalLines);
		boolean created = ledgerPostingRepository.saveIfNotExists(reversalPosting);
		if (!created) {
			ledgerBusinessMetrics.recordIdempotencyHit("reverse");
			log.info("ledger reversal already persisted reversalExternalId={}", reversalExternalId);
			return;
		}
		ledgerPostingPublisher.publish(reversalPosting);
		ledgerBusinessMetrics.recordLedgerReversalCreated();

		log.info(
				"ledger reversal created reversalEntryId={} originalEntryId={} reversalExternalId={} originalExternalId={}",
				reversalEntry.entryId(),
				originalEntry.entryId(),
				reversalEntry.externalId(),
				originalEntry.externalId()
		);
	}

	private LedgerPosting createPosting(LedgerEntry entry, List<LedgerEntryLine> lines) {
		long difference = Math.abs(lines.stream().mapToLong(LedgerEntryLine::signedAmountMinor).sum());
		ledgerBusinessMetrics.recordLedgerPostingBalanceDifference(difference, entry.entryType());
		try {
			return LedgerPosting.of(entry, lines);
		} catch (IllegalArgumentException exception) {
			if ("ledger posting must be balanced".equals(exception.getMessage())) {
				ledgerBusinessMetrics.recordLedgerPostingUnbalanced(entry.entryType());
			}
			ledgerBusinessMetrics.recordValidationFailure("reverse", exception.getMessage());
			throw exception;
		}
	}

	private String buildMetadata(LedgerReversalRequestedEvent event, LedgerEntry originalEntry)
			throws JsonProcessingException {
		return objectMapper.writeValueAsString(Map.of(
				"reversal_id", event.reversalId().toString(),
				"original_external_id", event.originalExternalId(),
				"original_entry_id", originalEntry.entryId(),
				"reason", event.reason()
		));
	}
}

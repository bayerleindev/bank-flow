package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.LedgerEntry;
import br.com.bankflow.ledger.domain.LedgerEntryLine;
import br.com.bankflow.ledger.domain.LedgerPosting;
import br.com.bankflow.ledger.domain.LedgerReversalRequestedEvent;
import br.com.bankflow.ledger.repositories.LedgerPostingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	public LedgerReversalService(
			LedgerPostingRepository ledgerPostingRepository,
			LedgerPostingPublisher ledgerPostingPublisher,
			NumericIdGenerator numericIdGenerator,
			Clock clock,
			ObjectMapper objectMapper
	) {
		this.ledgerPostingRepository = ledgerPostingRepository;
		this.ledgerPostingPublisher = ledgerPostingPublisher;
		this.numericIdGenerator = numericIdGenerator;
		this.clock = clock;
		this.objectMapper = objectMapper;
	}

	public void reverse(LedgerReversalRequestedEvent event) throws JsonProcessingException {
		event.validate();
		String reversalExternalId = event.reversalId().toString();
		if (ledgerPostingRepository.findByExternalId(reversalExternalId).isPresent()) {
			log.info("ledger reversal already processed reversalExternalId={}", reversalExternalId);
			return;
		}

		LedgerPosting originalPosting = ledgerPostingRepository.findByExternalId(event.originalExternalId())
				.orElseThrow(() -> new IllegalArgumentException(
						"original ledger posting not found original_external_id=%s".formatted(event.originalExternalId())
				));
		LedgerEntry originalEntry = originalPosting.entry();
		if ("REVERSAL".equals(originalEntry.entryType())) {
			throw new IllegalArgumentException("reversal entries cannot be reversed");
		}
		if (ledgerPostingRepository.reversalExistsFor(originalEntry.entryId())) {
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

		LedgerPosting reversalPosting = LedgerPosting.of(reversalEntry, reversalLines);
		boolean created = ledgerPostingRepository.saveIfNotExists(reversalPosting);
		if (!created) {
			log.info("ledger reversal already persisted reversalExternalId={}", reversalExternalId);
			return;
		}
		ledgerPostingPublisher.publish(reversalPosting);

		log.info(
				"ledger reversal created reversalEntryId={} originalEntryId={} reversalExternalId={} originalExternalId={}",
				reversalEntry.entryId(),
				originalEntry.entryId(),
				reversalEntry.externalId(),
				originalEntry.externalId()
		);
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

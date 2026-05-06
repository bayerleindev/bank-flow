package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.LedgerEntry;
import br.com.bankflow.ledger.domain.LedgerEntryLine;
import br.com.bankflow.ledger.domain.LedgerPosting;
import br.com.bankflow.ledger.domain.TransferPostedEvent;
import br.com.bankflow.ledger.repositories.LedgerAccountRepository;
import br.com.bankflow.ledger.repositories.LedgerPostingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;

@Service
public class LedgerMovementService {
	private static final Logger log = LoggerFactory.getLogger(LedgerMovementService.class);
	private static final long REVERSAL_OF_ENTRY_ID = 0L;

	private final LedgerAccountRepository ledgerAccountRepository;
	private final LedgerPostingRepository ledgerPostingRepository;
	private final LedgerPostingPublisher ledgerPostingPublisher;
	private final NumericIdGenerator numericIdGenerator;
	private final Clock clock;
	private final ObjectMapper objectMapper;

	public LedgerMovementService(
			LedgerAccountRepository ledgerAccountRepository,
			LedgerPostingRepository ledgerPostingRepository,
			LedgerPostingPublisher ledgerPostingPublisher,
			NumericIdGenerator numericIdGenerator,
			Clock clock,
			ObjectMapper objectMapper
	) {
		this.ledgerAccountRepository = ledgerAccountRepository;
		this.ledgerPostingRepository = ledgerPostingRepository;
		this.ledgerPostingPublisher = ledgerPostingPublisher;
		this.numericIdGenerator = numericIdGenerator;
		this.clock = clock;
		this.objectMapper = objectMapper;
	}

	public void postTransfer(TransferPostedEvent event) throws JsonProcessingException {
		event.validate();
		long now = clock.millis();
		long sourceAccountId = findAccountId(event, true);
		long destinationAccountId = findAccountId(event, false);
		LedgerEntry entry = LedgerEntry.from(
				numericIdGenerator.nextEntryId(),
				now,
				now,
				REVERSAL_OF_ENTRY_ID,
				buildMetadata(event),
				event
		);
		LedgerEntryLine debitLine = LedgerEntryLine.debit(
				numericIdGenerator.nextLineId(),
				entry.entryId(),
				sourceAccountId,
				event.amountCents(),
				event.currency(),
				"Debito transferencia para conta %s".formatted(event.destinationAccount()),
				now
		);
		LedgerEntryLine creditLine = LedgerEntryLine.credit(
				numericIdGenerator.nextLineId(),
				entry.entryId(),
				destinationAccountId,
				event.amountCents(),
				event.currency(),
				"Credito transferencia da conta %s".formatted(event.sourceAccount()),
				now
		);

		LedgerPosting posting = LedgerPosting.of(entry, List.of(debitLine, creditLine));
		boolean created = ledgerPostingRepository.saveIfNotExists(posting);
		if (!created) {
			log.info(
					"ledger movement already processed externalId={} sourceOwnerId={} destinationOwnerId={} amountCents={}",
					entry.externalId(),
					event.sourceOwnerId(),
					event.destinationOwnerId(),
					event.amountCents()
			);
			return;
		}
		ledgerPostingPublisher.publish(posting);

		log.info(
				"ledger movement created entryId={} debitLineId={} creditLineId={} externalId={} sourceOwnerId={} destinationOwnerId={} amountCents={}",
				entry.entryId(),
				debitLine.lineId(),
				creditLine.lineId(),
				entry.externalId(),
				event.sourceOwnerId(),
				event.destinationOwnerId(),
				event.amountCents()
		);
	}

	private String buildMetadata(TransferPostedEvent event) throws JsonProcessingException {
		return objectMapper.writeValueAsString(Map.of(
				"transfer_id", event.transferId().toString(),
				"source_owner_id", event.sourceOwnerId().toString(),
				"source_account", event.sourceAccount(),
				"destination_owner_id", event.destinationOwnerId().toString(),
				"destination_account", event.destinationAccount(),
				"amount_cents", event.amountCents(),
				"currency", event.currency(),
				"debit_account_code", "CUSTOMER_ACCOUNT_%s".formatted(event.sourceOwnerId()),
				"credit_account_code", "CUSTOMER_ACCOUNT_%s".formatted(event.destinationOwnerId())
		));
	}

	private long findAccountId(TransferPostedEvent event, boolean source) {
		java.util.UUID ownerId = source ? event.sourceOwnerId() : event.destinationOwnerId();
		String side = source ? "source" : "destination";
		return ledgerAccountRepository.findAccountIdByOwnerId(ownerId)
				.orElseThrow(() -> new IllegalArgumentException(
						"ledger account not found for %s_owner_id=%s".formatted(side, ownerId)
				));
	}
}

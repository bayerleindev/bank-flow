package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.LedgerEntry;
import br.com.bankflow.ledger.domain.LedgerEntryLine;
import br.com.bankflow.ledger.domain.LedgerPosting;
import br.com.bankflow.ledger.domain.TransferPostedEvent;
import br.com.bankflow.ledger.observability.BusinessCorrelation;
import br.com.bankflow.ledger.observability.LedgerBusinessMetrics;
import br.com.bankflow.ledger.repositories.LedgerAccountRepository;
import br.com.bankflow.ledger.repositories.LedgerPostingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LedgerMovementService {
	private static final Logger log = LoggerFactory.getLogger(LedgerMovementService.class);
	private static final long REVERSAL_OF_ENTRY_ID = 0L;
	private static final UUID EXTERNAL_INBOUND_SETTLEMENT_DIGITAL_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
	private static final String EXTERNAL_INBOUND_SETTLEMENT_ACCOUNT_CODE = "SETTLEMENT_EXTERNAL_INBOUND_BRL";

	private final LedgerAccountRepository ledgerAccountRepository;
	private final LedgerPostingRepository ledgerPostingRepository;
	private final LedgerPostingPublisher ledgerPostingPublisher;
	private final NumericIdGenerator numericIdGenerator;
	private final Clock clock;
	private final ObjectMapper objectMapper;
	private final LedgerBusinessMetrics ledgerBusinessMetrics;
	private final Tracer tracer;

	public LedgerMovementService(
			LedgerAccountRepository ledgerAccountRepository,
			LedgerPostingRepository ledgerPostingRepository,
			LedgerPostingPublisher ledgerPostingPublisher,
			NumericIdGenerator numericIdGenerator,
			Clock clock,
			ObjectMapper objectMapper
	) {
		this(
				ledgerAccountRepository,
				ledgerPostingRepository,
				ledgerPostingPublisher,
				numericIdGenerator,
				clock,
				objectMapper,
				new LedgerBusinessMetrics(new SimpleMeterRegistry()),
				null
		);
	}

	public LedgerMovementService(
			LedgerAccountRepository ledgerAccountRepository,
			LedgerPostingRepository ledgerPostingRepository,
			LedgerPostingPublisher ledgerPostingPublisher,
			NumericIdGenerator numericIdGenerator,
			Clock clock,
			ObjectMapper objectMapper,
			LedgerBusinessMetrics ledgerBusinessMetrics
	) {
		this(
				ledgerAccountRepository,
				ledgerPostingRepository,
				ledgerPostingPublisher,
				numericIdGenerator,
				clock,
				objectMapper,
				ledgerBusinessMetrics,
				null
		);
	}

	@Autowired
	public LedgerMovementService(
			LedgerAccountRepository ledgerAccountRepository,
			LedgerPostingRepository ledgerPostingRepository,
			LedgerPostingPublisher ledgerPostingPublisher,
			NumericIdGenerator numericIdGenerator,
			Clock clock,
			ObjectMapper objectMapper,
			LedgerBusinessMetrics ledgerBusinessMetrics,
			ObjectProvider<Tracer> tracerProvider
	) {
		this.ledgerAccountRepository = ledgerAccountRepository;
		this.ledgerPostingRepository = ledgerPostingRepository;
		this.ledgerPostingPublisher = ledgerPostingPublisher;
		this.numericIdGenerator = numericIdGenerator;
		this.clock = clock;
		this.objectMapper = objectMapper;
		this.ledgerBusinessMetrics = ledgerBusinessMetrics;
		this.tracer = tracerProvider == null ? null : tracerProvider.getIfAvailable();
	}

	public void postTransfer(TransferPostedEvent event) throws JsonProcessingException {
		try (BusinessCorrelation.Scope ignored = BusinessCorrelation.transfer(
				tracer,
				event.transferId(),
				event.sourceDigitalAccountId(),
				event.destinationDigitalAccountId()
		)) {
			try {
				event.validate();
			} catch (IllegalArgumentException exception) {
				ledgerBusinessMetrics.recordValidationFailure("post_transfer", exception.getMessage());
				throw exception;
			}
			long now = clock.millis();
			long occurredAt = event.transferCreatedAt() > 0 ? event.transferCreatedAt() : now;
			long sourceAccountId = findAccountId(event, true);
			long destinationAccountId = findAccountId(event, false);
			LedgerEntry entry = LedgerEntry.from(
					numericIdGenerator.nextEntryId(),
					occurredAt,
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

			LedgerPosting posting = createPosting(entry, List.of(debitLine, creditLine));
			boolean created = ledgerPostingRepository.saveIfNotExists(posting);
			if (!created) {
				ledgerBusinessMetrics.recordIdempotencyHit("post_transfer");
				LedgerPosting existingPosting = ledgerPostingRepository.findByExternalId(entry.externalId())
						.orElseThrow(() -> new IllegalStateException("existing ledger posting not found external_id=%s".formatted(entry.externalId())));
				ledgerPostingPublisher.publish(existingPosting);
				log.info(
						"ledger movement already processed externalId={} sourceDigitalAccountId={} destinationDigitalAccountId={} amountCents={}",
						entry.externalId(),
						event.sourceDigitalAccountId(),
						event.destinationDigitalAccountId(),
						event.amountCents()
				);
				return;
			}
			ledgerPostingPublisher.publish(posting);

			log.info(
					"ledger movement created entryId={} debitLineId={} creditLineId={} externalId={} sourceDigitalAccountId={} destinationDigitalAccountId={} amountCents={}",
					entry.entryId(),
					debitLine.lineId(),
					creditLine.lineId(),
					entry.externalId(),
					event.sourceDigitalAccountId(),
					event.destinationDigitalAccountId(),
					event.amountCents()
			);
		}
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
			ledgerBusinessMetrics.recordValidationFailure("post_transfer", exception.getMessage());
			throw exception;
		}
	}

	private String buildMetadata(TransferPostedEvent event) throws JsonProcessingException {
		return objectMapper.writeValueAsString(Map.of(
				"transfer_id", event.transferId().toString(),
				"source_digital_account_id", event.sourceDigitalAccountId().toString(),
				"source_account", event.sourceAccount(),
				"destination_digital_account_id", event.destinationDigitalAccountId().toString(),
				"destination_account", event.destinationAccount(),
				"amount_cents", event.amountCents(),
				"currency", event.currency(),
				"transfer_created_at", event.transferCreatedAt(),
				"debit_account_code", accountCodeFor(event.sourceDigitalAccountId()),
				"credit_account_code", accountCodeFor(event.destinationDigitalAccountId())
		));
	}

	private String accountCodeFor(UUID digitalAccountId) {
		if (EXTERNAL_INBOUND_SETTLEMENT_DIGITAL_ACCOUNT_ID.equals(digitalAccountId)) {
			return EXTERNAL_INBOUND_SETTLEMENT_ACCOUNT_CODE;
		}
		return "CUSTOMER_ACCOUNT_%s".formatted(digitalAccountId);
	}

	private long findAccountId(TransferPostedEvent event, boolean source) {
		java.util.UUID digitalAccountId = source ? event.sourceDigitalAccountId() : event.destinationDigitalAccountId();
		String side = source ? "source" : "destination";
		return ledgerAccountRepository.findAccountIdByDigitalAccountId(digitalAccountId)
				.orElseThrow(() -> {
					String message = "ledger account not found for %s_digital_account_id=%s".formatted(side, digitalAccountId);
					ledgerBusinessMetrics.recordValidationFailure("post_transfer", message);
					return new IllegalArgumentException(message);
				});
	}
}

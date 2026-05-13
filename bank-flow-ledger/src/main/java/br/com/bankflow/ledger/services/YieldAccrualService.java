package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.LedgerEntry;
import br.com.bankflow.ledger.domain.LedgerEntryLine;
import br.com.bankflow.ledger.domain.LedgerPosting;
import br.com.bankflow.ledger.domain.YieldAccrualEvent;
import br.com.bankflow.ledger.observability.LedgerBusinessMetrics;
import br.com.bankflow.ledger.repositories.LedgerAccountRepository;
import br.com.bankflow.ledger.repositories.LedgerPostingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class YieldAccrualService {
	private static final Logger log = LoggerFactory.getLogger(YieldAccrualService.class);
	private static final long REVERSAL_OF_ENTRY_ID = 0L;

	private final LedgerAccountRepository ledgerAccountRepository;
	private final LedgerPostingRepository ledgerPostingRepository;
	private final LedgerPostingPublisher ledgerPostingPublisher;
	private final NumericIdGenerator numericIdGenerator;
	private final Clock clock;
	private final ObjectMapper objectMapper;
	private final LedgerBusinessMetrics ledgerBusinessMetrics;
	private final UUID interestExpenseDigitalAccountId;

	public YieldAccrualService(
			LedgerAccountRepository ledgerAccountRepository,
			LedgerPostingRepository ledgerPostingRepository,
			LedgerPostingPublisher ledgerPostingPublisher,
			NumericIdGenerator numericIdGenerator,
			Clock clock,
			ObjectMapper objectMapper,
			UUID interestExpenseDigitalAccountId
	) {
		this(
				ledgerAccountRepository,
				ledgerPostingRepository,
				ledgerPostingPublisher,
				numericIdGenerator,
				clock,
				objectMapper,
				new LedgerBusinessMetrics(new SimpleMeterRegistry()),
				interestExpenseDigitalAccountId
		);
	}

	@Autowired
	public YieldAccrualService(
			LedgerAccountRepository ledgerAccountRepository,
			LedgerPostingRepository ledgerPostingRepository,
			LedgerPostingPublisher ledgerPostingPublisher,
			NumericIdGenerator numericIdGenerator,
			Clock clock,
			ObjectMapper objectMapper,
			LedgerBusinessMetrics ledgerBusinessMetrics,
			@Value("${bank-flow.ledger.accounts.interest-expense-digital-account-id}") UUID interestExpenseDigitalAccountId
	) {
		this.ledgerAccountRepository = ledgerAccountRepository;
		this.ledgerPostingRepository = ledgerPostingRepository;
		this.ledgerPostingPublisher = ledgerPostingPublisher;
		this.numericIdGenerator = numericIdGenerator;
		this.clock = clock;
		this.objectMapper = objectMapper;
		this.ledgerBusinessMetrics = ledgerBusinessMetrics;
		this.interestExpenseDigitalAccountId = interestExpenseDigitalAccountId;
	}

	public void postYieldAccrual(YieldAccrualEvent event) throws JsonProcessingException {
		try {
			event.validate();
		} catch (IllegalArgumentException exception) {
			ledgerBusinessMetrics.recordValidationFailure("post_yield_accrual", exception.getMessage());
			throw exception;
		}
		long now = clock.millis();
		long expenseAccountId = findAccountId(interestExpenseDigitalAccountId, "interest_expense");
		long customerAccountId = findAccountId(event.digitalAccountId(), "customer");
		LedgerEntry entry = LedgerEntry.yield(
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
				expenseAccountId,
				event.yieldAmountMinor(),
				event.currency(),
				"Despesa de rendimento CDI %s".formatted(event.referenceDate()),
				now
		);
		LedgerEntryLine creditLine = LedgerEntryLine.credit(
				numericIdGenerator.nextLineId(),
				entry.entryId(),
				customerAccountId,
				event.yieldAmountMinor(),
				event.currency(),
				"Rendimento CDI %s".formatted(event.referenceDate()),
				now
		);
		LedgerPosting posting = createPosting(entry, List.of(debitLine, creditLine));
		boolean created = ledgerPostingRepository.saveIfNotExists(posting);
		if (!created) {
			ledgerBusinessMetrics.recordIdempotencyHit("post_yield_accrual");
			LedgerPosting existingPosting = ledgerPostingRepository.findByExternalId(entry.externalId())
					.orElseThrow(() -> new IllegalStateException("existing yield posting not found external_id=%s".formatted(entry.externalId())));
			ledgerPostingPublisher.publish(existingPosting);
			return;
		}
		ledgerPostingPublisher.publish(posting);
		log.info(
				"yield accrual posted entryId={} accrualId={} digitalAccountId={} amountMinor={}",
				entry.entryId(),
				event.accrualId(),
				event.digitalAccountId(),
				event.yieldAmountMinor()
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
			ledgerBusinessMetrics.recordValidationFailure("post_yield_accrual", exception.getMessage());
			throw exception;
		}
	}

	private String buildMetadata(YieldAccrualEvent event) throws JsonProcessingException {
		return objectMapper.writeValueAsString(Map.ofEntries(
				Map.entry("accrual_id", event.accrualId().toString()),
				Map.entry("reference_date", event.referenceDate().toString()),
				Map.entry("digital_account_id", event.digitalAccountId().toString()),
				Map.entry("base_balance_minor", event.baseBalanceMinor()),
				Map.entry("yield_amount_minor", event.yieldAmountMinor()),
				Map.entry("currency", event.currency()),
				Map.entry("cdi_daily_rate_percent", event.cdiDailyRatePercent()),
				Map.entry("yield_cdi_percentage", event.yieldCdiPercentage()),
				Map.entry("interest_expense_digital_account_id", interestExpenseDigitalAccountId.toString()),
				Map.entry("debit_account_code", "INTEREST_EXPENSE_CDI_BRL"),
				Map.entry("credit_account_code", "CUSTOMER_ACCOUNT_%s".formatted(event.digitalAccountId()))
		));
	}

	private long findAccountId(UUID digitalAccountId, String side) {
		return ledgerAccountRepository.findAccountIdByDigitalAccountId(digitalAccountId)
				.orElseThrow(() -> {
					String message = "ledger account not found for %s_digital_account_id=%s".formatted(side, digitalAccountId);
					ledgerBusinessMetrics.recordValidationFailure("post_yield_accrual", message);
					return new IllegalArgumentException(message);
				});
	}
}

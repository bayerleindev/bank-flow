package br.com.bankflow.balance.services;

import br.com.bankflow.balance.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.balance.domain.LedgerPostingCreatedLine;
import br.com.bankflow.balance.observability.BalanceMetrics;
import br.com.bankflow.balance.repositories.BalanceProjectionRepository;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class LedgerPostingProjectionService {
	private static final Logger log = LoggerFactory.getLogger(LedgerPostingProjectionService.class);

	private final BalanceProjectionRepository balanceProjectionRepository;
	private final BalanceMetrics balanceMetrics;
	private final Clock clock;

	public LedgerPostingProjectionService(
			BalanceProjectionRepository balanceProjectionRepository,
			BalanceMetrics balanceMetrics,
			Clock clock
	) {
		this.balanceProjectionRepository = balanceProjectionRepository;
		this.balanceMetrics = balanceMetrics;
		this.clock = clock;
	}

	@Transactional
	public LedgerPostingProjectionResult project(LedgerPostingCreatedEvent event) {
		Timer.Sample sample = balanceMetrics.startProjectionTimer();
		try {
			event.validate();
			long now = clock.millis();
			boolean firstProcessing = balanceProjectionRepository.markProcessedIfAbsent(event, now);
			if (!firstProcessing) {
				log.info("ledger posting already projected entryId={} externalId={}", event.entryId(), event.externalId());
				balanceMetrics.recordProjection(sample, LedgerPostingProjectionResult.DUPLICATE, 0);
				return LedgerPostingProjectionResult.DUPLICATE;
			}

			for (LedgerPostingCreatedLine line : event.lines()) {
				if (line.entryId() != event.entryId()) {
					throw new IllegalArgumentException("line entry_id must match event entry_id");
				}
				balanceProjectionRepository.saveEntryLine(event, line);
				balanceProjectionRepository.applyPostedBalance(line, now);
			}

			log.info("ledger posting projected entryId={} externalId={} lines={}",
					event.entryId(),
					event.externalId(),
					event.lines().size()
			);
			balanceMetrics.recordProjectionLag(now - event.createdAt());
			balanceMetrics.recordProjection(sample, LedgerPostingProjectionResult.PROJECTED, event.lines().size());
			return LedgerPostingProjectionResult.PROJECTED;
		} catch (RuntimeException exception) {
			balanceMetrics.recordProjectionFailure(sample, exception);
			throw exception;
		}
	}
}

package br.com.bankflow.balance.worker.service;

import br.com.bankflow.balance.shared.kafka.LedgerJournalCreatedEvent;
import br.com.bankflow.balance.shared.repository.BalanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BalanceProjectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BalanceProjectionService.class);

    private final BalanceRepository balanceRepository;

    public BalanceProjectionService(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    public void applyJournal(LedgerJournalCreatedEvent event) {
        event.entries()
                .forEach(
                        entry -> {
                            boolean applied =
                                    balanceRepository.applyEntry(
                                            event.transferId(), entry, event.createdAt());
                            if (applied) {
                                LOGGER.info(
                                        "balance updated movementId={} accountId={} side={}",
                                        entry.movementId(),
                                        entry.accountId(),
                                        entry.side());
                            } else {
                                LOGGER.info(
                                        "duplicated journal entry ignored movementId={} accountId={} side={}",
                                        entry.movementId(),
                                        entry.accountId(),
                                        entry.side());
                            }
                        });
    }
}

package br.com.bankflow.ledger.service;

import br.com.bankflow.ledger.domain.LedgerAccount;
import br.com.bankflow.ledger.domain.LedgerAccountType;
import br.com.bankflow.ledger.kafka.AccountCreatedEvent;
import br.com.bankflow.ledger.repository.LedgerRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LedgerAccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LedgerAccountService.class);

    private final LedgerRepository ledgerRepository;
    private final Clock clock;
    private final String defaultCurrency;

    public LedgerAccountService(
            LedgerRepository ledgerRepository,
            Clock clock,
            @Value("${app.ledger.default-currency}") String defaultCurrency) {
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
        this.defaultCurrency = defaultCurrency;
    }

    public void createAccountRepresentation(AccountCreatedEvent event) {
        LedgerAccount account =
                new LedgerAccount(
                        event.accountId(),
                        event.documentNumber(),
                        event.branchNumber(),
                        event.accountNumber(),
                        event.accountDigit(),
                        defaultCurrency,
                        LedgerAccountType.LIABILITY,
                        Instant.now(clock));

        ledgerRepository.saveAccount(account);
        LOGGER.info("ledger account representation created accountId={}", event.accountId());
    }
}

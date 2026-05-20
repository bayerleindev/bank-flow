package br.com.bankflow.ledger.service;

import br.com.bankflow.ledger.domain.LedgerAccount;
import br.com.bankflow.ledger.domain.LedgerAccountType;
import br.com.bankflow.ledger.repository.LedgerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class LedgerBootstrapService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LedgerBootstrapService.class);

    private final LedgerRepository ledgerRepository;
    private final UUID externalSettlementAccountId;
    private final String defaultCurrency;
    private final Clock clock;

    public LedgerBootstrapService(
            LedgerRepository ledgerRepository,
            @Value("${app.ledger.external-settlement-account-id}") UUID externalSettlementAccountId,
            @Value("${app.ledger.default-currency}") String defaultCurrency,
            Clock clock) {
        this.ledgerRepository = ledgerRepository;
        this.externalSettlementAccountId = externalSettlementAccountId;
        this.defaultCurrency = defaultCurrency;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        ledgerRepository.bootstrapMigrations();
        ledgerRepository.saveAccount(externalSettlementAccount());
        LOGGER.info("ledger immudb bootstrap checked");
    }

    private LedgerAccount externalSettlementAccount() {
        Instant now = Instant.now(clock);
        return new LedgerAccount(
                externalSettlementAccountId,
                "BANK_FLOW_EXTERNAL_SETTLEMENT",
                "0000",
                "000000",
                "0",
                defaultCurrency,
                LedgerAccountType.ASSET,
                now);
    }
}

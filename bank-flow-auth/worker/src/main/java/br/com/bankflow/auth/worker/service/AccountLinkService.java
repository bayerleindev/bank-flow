package br.com.bankflow.auth.worker.service;

import br.com.bankflow.auth.kafka.AccountCreatedEvent;
import br.com.bankflow.auth.repository.AccountLinkRepository;
import br.com.bankflow.auth.service.AccountLink;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLinkService {

    private final AccountLinkRepository accountLinkRepository;
    private final Clock clock;

    public AccountLinkService(AccountLinkRepository accountLinkRepository, Clock clock) {
        this.accountLinkRepository = accountLinkRepository;
        this.clock = clock;
    }

    @Transactional
    public void link(AccountCreatedEvent event) {
        Instant now = Instant.now(clock);
        accountLinkRepository.upsert(
                new AccountLink(
                        event.documentNumber(),
                        event.accountId(),
                        event.branchNumber(),
                        event.accountNumber(),
                        event.accountDigit(),
                        now,
                        now));
    }
}

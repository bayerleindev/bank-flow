package br.com.bankflow.accounts.worker.service;

import br.com.bankflow.accounts.shared.kafka.AccountRequestedEvent;
import br.com.bankflow.accounts.shared.repository.AccountRepository;
import br.com.bankflow.accounts.worker.client.BaasClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountRequestedService {

    private final BaasClient baasClient;
    private final AccountRepository accountRepository;

    public AccountRequestedService(BaasClient baasClient, AccountRepository accountRepository) {
        this.baasClient = baasClient;
        this.accountRepository = accountRepository;
    }

    @Transactional
    @CircuitBreaker(name = "accountRequestedConsumer")
    @Bulkhead(name = "accountRequestedConsumer", type = Bulkhead.Type.SEMAPHORE)
    public void process(AccountRequestedEvent event) {
        baasClient.requestAccountCreation(event);
        accountRepository.markPendingBaas(event.accountId()).orElseThrow();
    }
}

package br.com.bankflow.accounts.worker.service;

import br.com.bankflow.accounts.shared.domain.Account;
import br.com.bankflow.accounts.shared.domain.AccountStatus;
import br.com.bankflow.accounts.shared.kafka.AccountCreationRequestedEvent;
import br.com.bankflow.accounts.shared.repository.AccountRepository;
import br.com.bankflow.accounts.worker.producer.AccountRequestedProducer;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountCreationRequestedService {

    private final AccountRepository accountRepository;
    private final AccountRequestedProducer accountRequestedProducer;

    public AccountCreationRequestedService(
            AccountRepository accountRepository,
            AccountRequestedProducer accountRequestedProducer) {
        this.accountRepository = accountRepository;
        this.accountRequestedProducer = accountRequestedProducer;
    }

    @Transactional
    public void process(AccountCreationRequestedEvent event) {
        accountRepository
                .findByOnboardingApplicationId(event.onboardingApplicationId())
                .or(() -> accountRepository.findByDocumentNumber(event.documentNumber()))
                .ifPresentOrElse(
                        existing -> {
                            if (existing.status() == AccountStatus.CREATION_REQUESTED) {
                                accountRequestedProducer.publish(existing);
                            }
                        },
                        () ->
                                accountRequestedProducer.publish(
                                        accountRepository.create(account(event))));
    }

    private Account account(AccountCreationRequestedEvent event) {
        return new Account(
                UUID.randomUUID(),
                event.fullName(),
                event.documentNumber(),
                event.email(),
                event.motherName(),
                event.socialName(),
                event.phoneNumber(),
                event.birthDate(),
                event.address(),
                event.politicallyExposed(),
                AccountStatus.CREATION_REQUESTED,
                null,
                null,
                null,
                null,
                event.onboardingApplicationId(),
                event.credentialsId(),
                null,
                null);
    }
}

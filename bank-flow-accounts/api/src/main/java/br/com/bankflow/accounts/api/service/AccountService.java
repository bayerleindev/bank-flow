package br.com.bankflow.accounts.api.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import br.com.bankflow.accounts.api.producer.AccountEventProducer;
import br.com.bankflow.accounts.shared.domain.Account;
import br.com.bankflow.accounts.shared.domain.AccountStatus;
import br.com.bankflow.accounts.shared.repository.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountEventProducer accountEventProducer;

    public AccountService(
            AccountRepository accountRepository, AccountEventProducer accountEventProducer) {
        this.accountRepository = accountRepository;
        this.accountEventProducer = accountEventProducer;
    }

    @Transactional
    public Account create(CreateAccountCommand command) {
        Account account =
                accountRepository.create(
                        new Account(
                                UUID.randomUUID(),
                                command.fullName(),
                                command.documentNumber(),
                                command.email(),
                                command.motherName(),
                                command.socialName(),
                                command.phoneNumber(),
                                command.birthDate(),
                                command.address(),
                                command.politicallyExposed(),
                                AccountStatus.CREATION_REQUESTED,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));

        accountEventProducer.publishRequested(account);
        return account;
    }

    public Account findById(UUID accountId) {
        return accountRepository
                .findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    @Transactional
    public void activate(BaasWebhookCommand command) {
        Account account =
                switch (command.status()) {
                    case ACTIVE ->
                            accountRepository
                                    .activate(
                                            command.accountId(),
                                            command.branchNumber(),
                                            command.accountNumber(),
                                            command.accountDigit())
                                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
                    case REJECTED ->
                            accountRepository
                                    .reject(command.accountId(), command.rejectionReason())
                                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
                    default ->
                            throw new IllegalArgumentException(
                                    "Unsupported webhook status: " + command.status());
                };

        if (account.status() == AccountStatus.ACTIVE) {
            accountEventProducer.publishCreated(account);
        }
    }
}

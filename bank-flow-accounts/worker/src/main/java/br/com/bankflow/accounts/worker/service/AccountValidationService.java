package br.com.bankflow.accounts.worker.service;

import br.com.bankflow.accounts.shared.domain.Account;
import br.com.bankflow.accounts.shared.domain.AccountStatus;
import br.com.bankflow.accounts.shared.kafka.AccountValidateCommand;
import br.com.bankflow.accounts.shared.kafka.AccountValidatedEvent;
import br.com.bankflow.accounts.shared.kafka.AccountValidationStatus;
import br.com.bankflow.accounts.shared.kafka.TransferParty;
import br.com.bankflow.accounts.shared.repository.AccountRepository;
import br.com.bankflow.accounts.worker.producer.AccountValidationProducer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountValidationService {

    private static final String BANK_FLOW_ISPB = "13935893";

    private final AccountRepository accountRepository;
    private final AccountValidationProducer accountValidationProducer;

    public AccountValidationService(
            AccountRepository accountRepository,
            AccountValidationProducer accountValidationProducer) {
        this.accountRepository = accountRepository;
        this.accountValidationProducer = accountValidationProducer;
    }

    public void validate(AccountValidateCommand command) {
        AccountValidatedEvent event = validateCommand(command);
        accountValidationProducer.publish(event);
    }

    private AccountValidatedEvent validateCommand(AccountValidateCommand command) {
        if (command.debitAccountId() == null) {
            return rejected(command.transferId(), "missing_debit_account_id");
        }

        Optional<Account> debitAccount = findActiveAccount(command.debitAccountId());
        if (debitAccount.isEmpty()) {
            return rejected(command.transferId(), "debit_account_not_found_or_inactive");
        }

        if (!BANK_FLOW_ISPB.equals(command.creditParty().bank())) {
            return approved(command.transferId(), debitAccount.orElseThrow().id(), null);
        }

        Optional<AccountReference> creditReference = parse(command.creditParty());
        if (creditReference.isEmpty()) {
            return rejected(command.transferId(), "invalid_credit_account_format");
        }

        Optional<Account> creditAccount = findActiveAccount(creditReference.orElseThrow());
        if (creditAccount.isEmpty()) {
            return rejected(command.transferId(), "credit_account_not_found_or_inactive");
        }

        return approved(
                command.transferId(),
                debitAccount.orElseThrow().id(),
                creditAccount.orElseThrow().id());
    }

    private Optional<Account> findActiveAccount(UUID accountId) {
        return accountRepository
                .findById(accountId)
                .filter(account -> account.status() == AccountStatus.ACTIVE);
    }

    private Optional<Account> findActiveAccount(AccountReference reference) {
        return accountRepository.findActiveByBranchAccountAndDigit(
                reference.branchNumber(), reference.accountNumber(), reference.accountDigit());
    }

    private Optional<AccountReference> parse(TransferParty transferParty) {
        String[] accountParts = transferParty.account().split("-", 2);
        if (accountParts.length != 2 || accountParts[0].isBlank() || accountParts[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(
                new AccountReference(transferParty.branch(), accountParts[0], accountParts[1]));
    }

    private AccountValidatedEvent approved(
            UUID transferId, UUID debitAccountId, UUID creditAccountId) {
        return new AccountValidatedEvent(
                transferId,
                AccountValidationStatus.APPROVED,
                null,
                debitAccountId,
                creditAccountId,
                Instant.now());
    }

    private AccountValidatedEvent rejected(UUID transferId, String reason) {
        return new AccountValidatedEvent(
                transferId, AccountValidationStatus.REJECTED, reason, null, null, Instant.now());
    }

    private record AccountReference(
            String branchNumber, String accountNumber, String accountDigit) {}
}

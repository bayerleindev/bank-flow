package br.com.bankflow.accounts.api.dto.response;

import br.com.bankflow.accounts.shared.domain.Account;
import br.com.bankflow.accounts.shared.domain.AccountStatus;
import java.time.LocalDate;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String fullName,
        String documentNumber,
        String email,
        String motherName,
        String socialName,
        String phoneNumber,
        LocalDate birthDate,
        String address,
        boolean politicallyExposed,
        AccountStatus status,
        String branchNumber,
        String accountNumber,
        String accountDigit,
        String rejectionReason) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.id(),
                account.fullName(),
                account.documentNumber(),
                account.email(),
                account.motherName(),
                account.socialName(),
                account.phoneNumber(),
                account.birthDate(),
                account.address(),
                account.politicallyExposed(),
                account.status(),
                account.branchNumber(),
                account.accountNumber(),
                account.accountDigit(),
                account.rejectionReason());
    }
}

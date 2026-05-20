package br.com.bankflow.accounts.api.dto.response;

import br.com.bankflow.accounts.shared.domain.Account;
import br.com.bankflow.accounts.shared.domain.AccountStatus;
import java.util.UUID;

public record AccountCreatedResponse(UUID id, AccountStatus status) {

    public static AccountCreatedResponse from(Account account) {
        return new AccountCreatedResponse(account.id(), account.status());
    }
}

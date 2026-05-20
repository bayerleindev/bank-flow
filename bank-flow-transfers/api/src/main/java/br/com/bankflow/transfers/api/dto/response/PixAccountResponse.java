package br.com.bankflow.transfers.api.dto.response;

import br.com.bankflow.transfers.shared.domain.PixAccount;

public record PixAccountResponse(String bank, String account, String branch) {

    public static PixAccountResponse from(PixAccount account) {
        return new PixAccountResponse(account.bank(), account.account(), account.branch());
    }
}

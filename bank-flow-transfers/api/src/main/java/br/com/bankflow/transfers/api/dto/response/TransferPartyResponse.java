package br.com.bankflow.transfers.api.dto.response;

import br.com.bankflow.transfers.shared.domain.TransferParty;

public record TransferPartyResponse(String bank, String account, String branch) {

    public static TransferPartyResponse from(TransferParty transferParty) {
        return new TransferPartyResponse(
                transferParty.bank(), transferParty.account(), transferParty.branch());
    }
}

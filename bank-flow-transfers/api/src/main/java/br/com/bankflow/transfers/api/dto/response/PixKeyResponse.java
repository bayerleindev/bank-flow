package br.com.bankflow.transfers.api.dto.response;

import br.com.bankflow.transfers.shared.domain.PixKeyInfo;

public record PixKeyResponse(
        PixAccountResponse account, PixOwnerResponse owner, String endToEndId) {

    public static PixKeyResponse from(PixKeyInfo keyInfo) {
        return new PixKeyResponse(
                PixAccountResponse.from(keyInfo.account()),
                PixOwnerResponse.from(keyInfo.owner()),
                keyInfo.endToEndId());
    }
}

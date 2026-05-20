package br.com.bankflow.transfers.api.dto.response;

import br.com.bankflow.transfers.shared.domain.PixOwner;

public record PixOwnerResponse(String name, String maskedDocument) {

    public static PixOwnerResponse from(PixOwner owner) {
        return new PixOwnerResponse(owner.name(), owner.maskedDocument());
    }
}

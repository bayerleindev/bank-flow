package br.com.bankflow.transfers.api.service;

import br.com.bankflow.transfers.api.dto.response.PixAccountResponse;
import br.com.bankflow.transfers.api.dto.response.PixKeyResponse;
import br.com.bankflow.transfers.shared.domain.TransferParty;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class PixKeyTransferValidator {

    private final PixKeyCacheService pixKeyCacheService;

    public PixKeyTransferValidator(PixKeyCacheService pixKeyCacheService) {
        this.pixKeyCacheService = pixKeyCacheService;
    }

    public void validate(CreateTransferCommand command) {
        PixKeyResponse pixKeyResponse =
                pixKeyCacheService
                        .findByEndToEndId(command.idempotencyKey())
                        .orElseThrow(
                                () -> new PixKeyValidationException("pix_key_cache_not_found"));

        if (!Objects.equals(command.idempotencyKey(), pixKeyResponse.endToEndId())) {
            throw new PixKeyValidationException("pix_key_end_to_end_mismatch");
        }

        if (!sameAccount(command.creditParty(), pixKeyResponse.account())) {
            throw new PixKeyValidationException("pix_key_credit_party_mismatch");
        }
    }

    private static boolean sameAccount(TransferParty creditParty, PixAccountResponse account) {
        return Objects.equals(creditParty.bank(), account.bank())
                && Objects.equals(creditParty.account(), account.account())
                && Objects.equals(creditParty.branch(), account.branch());
    }
}

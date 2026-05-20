package br.com.bankflow.transfers.api.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.bankflow.transfers.api.dto.response.PixAccountResponse;
import br.com.bankflow.transfers.api.dto.response.PixKeyResponse;
import br.com.bankflow.transfers.api.dto.response.PixOwnerResponse;
import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PixKeyTransferValidatorTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock private PixKeyCacheService pixKeyCacheService;

    @Test
    void shouldNotCreateTransferWhenPixKeyCacheIsMissing() {
        PixKeyTransferValidator validator = new PixKeyTransferValidator(pixKeyCacheService);
        when(pixKeyCacheService.findByEndToEndId("E2E123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(command("260", "12345-6", "0001")))
                .isInstanceOf(PixKeyValidationException.class)
                .hasMessage("pix_key_cache_not_found");
    }

    @Test
    void shouldNotCreateTransferWhenCreditPartyDoesNotMatchPixKeyCache() {
        PixKeyTransferValidator validator = new PixKeyTransferValidator(pixKeyCacheService);
        when(pixKeyCacheService.findByEndToEndId("E2E123")).thenReturn(Optional.of(response()));

        assertThatThrownBy(() -> validator.validate(command("260", "99999-9", "0001")))
                .isInstanceOf(PixKeyValidationException.class)
                .hasMessage("pix_key_credit_party_mismatch");
    }

    @Test
    void shouldValidateTransferWhenCreditPartyMatchesPixKeyCache() {
        PixKeyTransferValidator validator = new PixKeyTransferValidator(pixKeyCacheService);
        when(pixKeyCacheService.findByEndToEndId("E2E123")).thenReturn(Optional.of(response()));

        assertThatCode(() -> validator.validate(command("260", "12345-6", "0001")))
                .doesNotThrowAnyException();
    }

    private static CreateTransferCommand command(String bank, String account, String branch) {
        return new CreateTransferCommand(
                ACCOUNT_ID,
                new TransferParty(bank, account, branch),
                "E2E123",
                1000,
                "pix",
                "BRL",
                TransferType.PIX);
    }

    private static PixKeyResponse response() {
        return new PixKeyResponse(
                new PixAccountResponse("260", "12345-6", "0001"),
                new PixOwnerResponse("Ada", "***.123.456-**"),
                "E2E123");
    }
}

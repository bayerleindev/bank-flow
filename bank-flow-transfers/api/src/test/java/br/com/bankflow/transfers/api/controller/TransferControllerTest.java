package br.com.bankflow.transfers.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.bankflow.transfers.api.dto.request.CreateTransferRequest;
import br.com.bankflow.transfers.api.service.CreateTransferCommand;
import br.com.bankflow.transfers.api.service.TransferAuthenticationException;
import br.com.bankflow.transfers.api.service.TransferService;
import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"PMD.TooManyStaticImports", "PMD.UnitTestContainsTooManyAsserts"})
class TransferControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TRANSFER_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Mock private TransferService transferService;

    @Test
    void shouldCreateTransferUsingDebitAccountIdFromJwt() {
        TransferController controller = new TransferController(transferService);
        CreateTransferRequest request = request();
        Transfer transfer = transfer("PIX_REQUESTED");
        when(transferService.create(any())).thenReturn(transfer);

        ResponseEntity<?> response =
                controller.create(jwt(ACCOUNT_ID.toString()), "E2E123", request);

        ArgumentCaptor<CreateTransferCommand> commandCaptor =
                ArgumentCaptor.forClass(CreateTransferCommand.class);
        verify(transferService).create(commandCaptor.capture());
        assertThat(commandCaptor.getValue().debitAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(commandCaptor.getValue().creditParty())
                .isEqualTo(new TransferParty("260", "12345-6", "0001"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void shouldNotCreateTransferWhenJwtDoesNotHaveAccountId() {
        TransferController controller = new TransferController(transferService);

        assertThatThrownBy(() -> controller.create(jwt(null), "E2E123", request()))
                .isInstanceOf(TransferAuthenticationException.class);
    }

    @Test
    void shouldNotCreateTransferWithoutBearerToken() {
        TransferController controller = new TransferController(transferService);

        assertThatThrownBy(() -> controller.create(null, "E2E123", request()))
                .isInstanceOf(TransferAuthenticationException.class);
    }

    private static CreateTransferRequest request() {
        return new CreateTransferRequest(
                new CreateTransferRequest.TransferPartyRequest("260", "12345-6", "0001"),
                1000,
                "pix",
                "BRL",
                TransferType.PIX);
    }

    private static Jwt jwt(String accountId) {
        Jwt.Builder builder =
                Jwt.withTokenValue("token")
                        .header("alg", "RS256")
                        .issuer("http://localhost:8090")
                        .subject("subject")
                        .issuedAt(Instant.parse("2026-05-20T12:00:00Z"))
                        .expiresAt(Instant.parse("2026-05-20T12:15:00Z"));
        if (accountId != null) {
            builder.claim("account_id", accountId);
        }
        return builder.claims(claims -> claims.putAll(Map.of("scope", "transfers:create"))).build();
    }

    private static Transfer transfer(String status) {
        Instant now = Instant.parse("2026-05-20T12:00:00Z");
        return new Transfer(
                TRANSFER_ID,
                null,
                new TransferParty("260", "12345-6", "0001"),
                "E2E123",
                1000,
                "pix",
                "BRL",
                TransferType.PIX,
                status,
                null,
                ACCOUNT_ID,
                null,
                now,
                now);
    }
}

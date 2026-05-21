package br.com.bankflow.transfers.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.bankflow.transfers.api.config.SecurityConfig;
import br.com.bankflow.transfers.api.service.BaasTransferWebhookService;
import br.com.bankflow.transfers.api.service.PixKeyService;
import br.com.bankflow.transfers.api.service.TransferService;
import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = {
            TransferController.class,
            PixKeyController.class,
            BaasTransferWebhookController.class,
            SecurityConfig.class,
            ObjectMapper.class
        })
@AutoConfigureMockMvc
@ImportAutoConfiguration(
        classes = {
            JacksonAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
@TestPropertySource(
        properties = {
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8090",
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8090/.well-known/jwks.json"
        })
class TransferSecurityTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID TRANSFER_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TransferService transferService;

    @MockitoBean private PixKeyService pixKeyService;

    @MockitoBean private BaasTransferWebhookService baasTransferWebhookService;

    @Test
    void shouldRejectTransferLookupWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/transfers/{transferId}", TRANSFER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTransferLookupForDifferentAccount() throws Exception {
        when(transferService.findById(TRANSFER_ID)).thenReturn(transfer(OTHER_ACCOUNT_ID));

        mockMvc.perform(
                        get("/transfers/{transferId}", TRANSFER_ID)
                                .with(
                                        jwt().jwt(
                                                        jwt ->
                                                                jwt.claim(
                                                                        "account_id",
                                                                        ACCOUNT_ID.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnTransferForAuthenticatedAccount() throws Exception {
        when(transferService.findById(TRANSFER_ID)).thenReturn(transfer(ACCOUNT_ID));

        mockMvc.perform(
                        get("/transfers/{transferId}", TRANSFER_ID)
                                .with(
                                        jwt().jwt(
                                                        jwt ->
                                                                jwt.claim(
                                                                        "account_id",
                                                                        ACCOUNT_ID.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectPixKeyLookupWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/keys/{key}", "pix@example.com")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowBaasWebhookWithoutBearerToken() throws Exception {
        mockMvc.perform(
                        post("/baas/webhooks/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "transferId": "00000000-0000-0000-0000-000000000303",
                                          "status": "COMPLETED"
                                        }
                                        """))
                .andExpect(status().isAccepted());
    }

    private static Transfer transfer(UUID debitAccountId) {
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
                "PIX_REQUESTED",
                null,
                debitAccountId,
                null,
                now,
                now);
    }
}

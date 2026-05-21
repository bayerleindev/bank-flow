package br.com.bankflow.balance.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.bankflow.balance.api.config.SecurityConfig;
import br.com.bankflow.balance.api.service.BalanceService;
import br.com.bankflow.balance.shared.domain.Balance;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {BalanceController.class, SecurityConfig.class, ObjectMapper.class})
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
class BalanceSecurityTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BalanceService balanceService;

    @Test
    void shouldRejectBalanceLookupWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/balances/{accountId}", ACCOUNT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectBalanceLookupForDifferentAccount() throws Exception {
        mockMvc.perform(
                        get("/balances/{accountId}", OTHER_ACCOUNT_ID)
                                .with(
                                        jwt().jwt(
                                                        jwt ->
                                                                jwt.claim(
                                                                        "account_id",
                                                                        ACCOUNT_ID.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnBalanceForAuthenticatedAccount() throws Exception {
        when(balanceService.findByAccountId(ACCOUNT_ID))
                .thenReturn(
                        List.of(
                                new Balance(
                                        ACCOUNT_ID,
                                        "BRL",
                                        1000,
                                        100,
                                        Instant.parse("2026-05-20T12:00:00Z"))));

        mockMvc.perform(
                        get("/balances/{accountId}", ACCOUNT_ID)
                                .with(
                                        jwt().jwt(
                                                        jwt ->
                                                                jwt.claim(
                                                                        "account_id",
                                                                        ACCOUNT_ID.toString()))))
                .andExpect(status().isOk());
    }
}

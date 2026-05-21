package br.com.bankflow.accounts.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.bankflow.accounts.api.config.SecurityConfig;
import br.com.bankflow.accounts.api.service.AccountService;
import br.com.bankflow.accounts.shared.domain.Account;
import br.com.bankflow.accounts.shared.domain.AccountStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
            AccountController.class,
            BaasWebhookController.class,
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
@SuppressWarnings({"PMD.TooManyStaticImports", "PMD.UnitTestShouldIncludeAssert"})
class AccountSecurityTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AccountService accountService;

    @Test
    void shouldCreateAccountWithoutBearerToken() throws Exception {
        when(accountService.create(any())).thenReturn(account());

        mockMvc.perform(
                        post("/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createAccountRequest()))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldCreateAccountWithBearerToken() throws Exception {
        when(accountService.create(any())).thenReturn(account());

        mockMvc.perform(
                        post("/accounts")
                                .with(
                                        jwt().jwt(
                                                        jwt ->
                                                                jwt.claim(
                                                                        "account_id",
                                                                        ACCOUNT_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createAccountRequest()))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldRejectAccountLookupWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}", ACCOUNT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAccountLookupForDifferentAccount() throws Exception {
        mockMvc.perform(
                        get("/accounts/{accountId}", OTHER_ACCOUNT_ID)
                                .with(
                                        jwt().jwt(
                                                        jwt ->
                                                                jwt.claim(
                                                                        "account_id",
                                                                        ACCOUNT_ID.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowBaasWebhookWithoutBearerToken() throws Exception {
        mockMvc.perform(
                        post("/webhooks/baas/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "accountId": "00000000-0000-0000-0000-000000000101",
                                          "status": "ACTIVE",
                                          "branchNumber": "0001",
                                          "accountNumber": "12345",
                                          "accountDigit": "6"
                                        }
                                        """))
                .andExpect(status().isNoContent());
    }

    private static String createAccountRequest() {
        return """
                {
                  "fullName": "Ada Lovelace",
                  "documentNumber": "12345678900",
                  "email": "ada@example.com",
                  "motherName": "Anne",
                  "phoneNumber": "+5511999999999",
                  "birthDate": "10-12-1815",
                  "address": "Rua Um",
                  "isPoliticallyExposed": false
                }
                """;
    }

    private static Account account() {
        return new Account(
                ACCOUNT_ID,
                "Ada Lovelace",
                "12345678900",
                "ada@example.com",
                "Anne",
                null,
                "+5511999999999",
                LocalDate.parse("1815-12-10"),
                "Rua Um",
                false,
                AccountStatus.CREATION_REQUESTED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}

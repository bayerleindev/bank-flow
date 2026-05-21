package br.com.bankflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.bankflow.auth.repository.AccountLinkRepository;
import br.com.bankflow.auth.repository.CredentialRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordAuthenticationServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CREDENTIAL_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID APPLICATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000303");

    @Mock private CredentialRepository credentialRepository;
    @Mock private AccountLinkRepository accountLinkRepository;

    @Test
    void shouldAuthenticateUsingDocumentNumberAndPassword() {
        PasswordAuthenticationService service =
                new PasswordAuthenticationService(credentialRepository, accountLinkRepository);
        when(credentialRepository.findByDocumentNumber("12345678900"))
                .thenReturn(Optional.of(credential("S3nh@Forte")));
        when(accountLinkRepository.findAccountIdByDocumentNumber("12345678900"))
                .thenReturn(Optional.of(ACCOUNT_ID));

        UUID accountId = service.authenticate("12345678900", "S3nh@Forte");

        assertThat(accountId).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void shouldRejectInvalidPassword() {
        PasswordAuthenticationService service =
                new PasswordAuthenticationService(credentialRepository, accountLinkRepository);
        when(credentialRepository.findByDocumentNumber("12345678900"))
                .thenReturn(Optional.of(credential("S3nh@Forte")));

        assertThatThrownBy(() -> service.authenticate("12345678900", "errada"))
                .isInstanceOf(AuthException.class)
                .hasMessage("invalid_credentials");
    }

    @Test
    void shouldRejectWhenAccountWasNotLinkedYet() {
        PasswordAuthenticationService service =
                new PasswordAuthenticationService(credentialRepository, accountLinkRepository);
        when(credentialRepository.findByDocumentNumber("12345678900"))
                .thenReturn(Optional.of(credential("S3nh@Forte")));
        when(accountLinkRepository.findAccountIdByDocumentNumber("12345678900"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("12345678900", "S3nh@Forte"))
                .isInstanceOf(AuthException.class)
                .hasMessage("account_not_active");
    }

    private Credential credential(String password) {
        Instant now = Instant.parse("2026-05-21T12:00:00Z");
        return new Credential(
                CREDENTIAL_ID,
                APPLICATION_ID,
                "12345678900",
                new BCryptPasswordEncoder(12).encode(password),
                "ACTIVE",
                now,
                now);
    }
}

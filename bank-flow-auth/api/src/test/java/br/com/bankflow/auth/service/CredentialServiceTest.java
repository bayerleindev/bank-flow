package br.com.bankflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.bankflow.auth.repository.CredentialRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class CredentialServiceTest {

    private static final UUID APPLICATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final Instant NOW = Instant.parse("2026-05-21T12:00:00Z");

    @Mock private CredentialRepository credentialRepository;

    @Test
    void shouldStoreBCryptHashInsteadOfRawPassword() {
        CredentialService service =
                new CredentialService(credentialRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(credentialRepository.create(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Credential credential = service.create(APPLICATION_ID, "12345678900", "S3nh@Forte");

        ArgumentCaptor<Credential> credentialCaptor = ArgumentCaptor.forClass(Credential.class);
        org.mockito.Mockito.verify(credentialRepository).create(credentialCaptor.capture());
        assertThat(credential.passwordHash()).isNotEqualTo("S3nh@Forte");
        assertThat(new BCryptPasswordEncoder().matches("S3nh@Forte", credential.passwordHash()))
                .isTrue();
        assertThat(credentialCaptor.getValue().createdAt()).isEqualTo(NOW);
    }
}

package br.com.bankflow.auth.service;

import br.com.bankflow.auth.repository.CredentialRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredentialService {

    private static final String ACTIVE = "ACTIVE";

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private final Clock clock;

    public CredentialService(CredentialRepository credentialRepository, Clock clock) {
        this.credentialRepository = credentialRepository;
        this.clock = clock;
    }

    @Transactional
    public Credential create(UUID onboardingApplicationId, String documentNumber, String password) {
        Instant now = Instant.now(clock);
        Credential credential =
                new Credential(
                        UUID.randomUUID(),
                        onboardingApplicationId,
                        documentNumber,
                        passwordEncoder.encode(password),
                        ACTIVE,
                        now,
                        now);
        return credentialRepository.create(credential);
    }
}

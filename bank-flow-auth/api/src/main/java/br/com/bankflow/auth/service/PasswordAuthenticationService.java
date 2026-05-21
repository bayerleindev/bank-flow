package br.com.bankflow.auth.service;

import br.com.bankflow.auth.repository.AccountLinkRepository;
import br.com.bankflow.auth.repository.CredentialRepository;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordAuthenticationService {

    private static final String ACTIVE = "ACTIVE";

    private final CredentialRepository credentialRepository;
    private final AccountLinkRepository accountLinkRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public PasswordAuthenticationService(
            CredentialRepository credentialRepository,
            AccountLinkRepository accountLinkRepository) {
        this.credentialRepository = credentialRepository;
        this.accountLinkRepository = accountLinkRepository;
    }

    public UUID authenticate(String documentNumber, String password) {
        Credential credential =
                credentialRepository
                        .findByDocumentNumber(documentNumber)
                        .filter(found -> ACTIVE.equals(found.status()))
                        .orElseThrow(() -> new AuthException("invalid_credentials"));
        if (!passwordEncoder.matches(password, credential.passwordHash())) {
            throw new AuthException("invalid_credentials");
        }
        return accountLinkRepository
                .findAccountIdByDocumentNumber(documentNumber)
                .orElseThrow(() -> new AuthException("account_not_active"));
    }
}

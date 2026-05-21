package br.com.bankflow.auth.controller;

import br.com.bankflow.auth.dto.CreateCredentialsRequest;
import br.com.bankflow.auth.dto.CreateCredentialsResponse;
import br.com.bankflow.auth.service.AuthException;
import br.com.bankflow.auth.service.Credential;
import br.com.bankflow.auth.service.CredentialService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CredentialsController {

    private static final String ISSUER_KEY_HEADER = "X-Auth-Issuer-Key";

    private final CredentialService credentialService;
    private final String issuerKey;

    public CredentialsController(
            CredentialService credentialService,
            @Value("${app.auth.issuer-key}") String issuerKey) {
        this.credentialService = credentialService;
        this.issuerKey = issuerKey;
    }

    @PostMapping("/credentials")
    public ResponseEntity<CreateCredentialsResponse> create(
            @RequestHeader(name = ISSUER_KEY_HEADER, required = false) String requestedIssuerKey,
            @Valid @RequestBody CreateCredentialsRequest request) {
        if (!StringUtils.hasText(requestedIssuerKey) || !issuerKey.equals(requestedIssuerKey)) {
            throw new AuthException("invalid_issuer_key");
        }

        Credential credential =
                credentialService.create(
                        request.onboardingApplicationId(),
                        request.documentNumber(),
                        request.password());
        return ResponseEntity.created(URI.create("/credentials/" + credential.id()))
                .body(new CreateCredentialsResponse(credential.id(), credential.documentNumber()));
    }
}

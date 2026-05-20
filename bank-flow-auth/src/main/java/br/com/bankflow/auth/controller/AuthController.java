package br.com.bankflow.auth.controller;

import br.com.bankflow.auth.dto.CreateTokenRequest;
import br.com.bankflow.auth.dto.CreateTokenResponse;
import br.com.bankflow.auth.service.AuthException;
import br.com.bankflow.auth.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private static final String ISSUER_KEY_HEADER = "X-Auth-Issuer-Key";

    private final TokenService tokenService;
    private final String issuerKey;

    public AuthController(
            TokenService tokenService, @Value("${app.auth.issuer-key}") String issuerKey) {
        this.tokenService = tokenService;
        this.issuerKey = issuerKey;
    }

    @PostMapping("/tokens")
    public ResponseEntity<CreateTokenResponse> createToken(
            @RequestHeader(name = ISSUER_KEY_HEADER, required = false) String requestedIssuerKey,
            @Valid @RequestBody CreateTokenRequest request) {
        if (!StringUtils.hasText(requestedIssuerKey) || !issuerKey.equals(requestedIssuerKey)) {
            throw new AuthException("invalid_issuer_key");
        }

        return ResponseEntity.ok(tokenService.create(request.accountId()));
    }
}

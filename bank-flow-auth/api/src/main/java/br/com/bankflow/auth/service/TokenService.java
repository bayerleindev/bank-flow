package br.com.bankflow.auth.service;

import br.com.bankflow.auth.dto.CreateTokenResponse;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private static final String BEARER = "Bearer";
    private static final String TOKEN_SCOPE = "transfers:create";

    private final RsaKeyService rsaKeyService;
    private final PasswordAuthenticationService authenticationService;
    private final Clock clock;
    private final String issuer;
    private final Duration ttl;

    public TokenService(
            RsaKeyService rsaKeyService,
            PasswordAuthenticationService authenticationService,
            Clock clock,
            @Value("${app.auth.jwt.issuer}") String issuer,
            @Value("${app.auth.jwt.ttl}") Duration ttl) {
        this.rsaKeyService = rsaKeyService;
        this.authenticationService = authenticationService;
        this.clock = clock;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public CreateTokenResponse create(String documentNumber, String password) {
        return create(authenticationService.authenticate(documentNumber, password));
    }

    private CreateTokenResponse create(UUID accountId) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(ttl);
        SignedJWT signedJwt = new SignedJWT(header(), claims(accountId, issuedAt, expiresAt));

        try {
            signedJwt.sign(new RSASSASigner(rsaKeyService.privateKey()));
            return new CreateTokenResponse(signedJwt.serialize(), BEARER, ttl.toSeconds());
        } catch (JOSEException exception) {
            throw new AuthException("token_signing_failed", exception);
        }
    }

    private JWSHeader header() {
        return new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKeyService.privateKey().getKeyID())
                .type(JOSEObjectType.JWT)
                .build();
    }

    private JWTClaimsSet claims(UUID accountId, Instant issuedAt, Instant expiresAt) {
        String accountIdValue = accountId.toString();
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(accountIdValue)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .claim("customer_id", accountIdValue)
                .claim("account_id", accountIdValue)
                .claim("scope", TOKEN_SCOPE)
                .build();
    }
}

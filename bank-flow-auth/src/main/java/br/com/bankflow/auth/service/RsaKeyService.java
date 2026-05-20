package br.com.bankflow.auth.service;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public final class RsaKeyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RsaKeyService.class);
    private static final String RSA = "RSA";
    private static final int RSA_KEY_SIZE = 3072;

    private final RSAKey rsaKey;

    public RsaKeyService(
            @Value("${app.auth.jwt.private-key}") String privateKeyPem,
            @Value("${app.auth.jwt.key-id}") String keyId) {
        rsaKey = createRsaKey(privateKeyPem, keyId);
    }

    public RSAKey privateKey() {
        return rsaKey;
    }

    public Map<String, Object> jwks() {
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }

    private static RSAKey createRsaKey(String privateKeyPem, String keyId) {
        try {
            KeyPair keyPair = resolveKeyPair(privateKeyPem);
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(new Algorithm("RS256"))
                    .keyID(keyId)
                    .build();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new AuthException("jwt_key_initialization_failed", exception);
        }
    }

    private static KeyPair resolveKeyPair(String privateKeyPem)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (StringUtils.hasText(privateKeyPem)) {
            return parsePrivateKey(privateKeyPem);
        }

        LOGGER.warn("AUTH_JWT_PRIVATE_KEY is not set; using an ephemeral development RSA key");
        KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA);
        generator.initialize(RSA_KEY_SIZE);
        return generator.generateKeyPair();
    }

    private static KeyPair parsePrivateKey(String privateKeyPem)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        String normalized =
                privateKeyPem
                        .replace("\\n", "\n")
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA);
        RSAPrivateKey privateKey =
                (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));

        if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
            throw new InvalidKeySpecException("RSA private key must include CRT parameters");
        }

        RSAPublicKeySpec publicKeySpec =
                new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
        return new KeyPair(publicKey, privateKey);
    }
}

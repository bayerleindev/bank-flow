package br.com.bankflow.auth.controller;

import br.com.bankflow.auth.service.RsaKeyService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

    private final RsaKeyService rsaKeyService;

    public JwksController(RsaKeyService rsaKeyService) {
        this.rsaKeyService = rsaKeyService;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return rsaKeyService.jwks();
    }
}

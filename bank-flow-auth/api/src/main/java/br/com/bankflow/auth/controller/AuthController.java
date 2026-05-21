package br.com.bankflow.auth.controller;

import br.com.bankflow.auth.dto.CreateTokenRequest;
import br.com.bankflow.auth.dto.CreateTokenResponse;
import br.com.bankflow.auth.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final TokenService tokenService;

    public AuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/tokens")
    public ResponseEntity<CreateTokenResponse> createToken(
            @Valid @RequestBody CreateTokenRequest request) {
        return ResponseEntity.ok(tokenService.create(request.documentNumber(), request.password()));
    }
}

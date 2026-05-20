package br.com.bankflow.accounts.api.controller;

import br.com.bankflow.accounts.api.dto.request.BaasAccountWebhookRequest;
import br.com.bankflow.accounts.api.service.AccountService;
import br.com.bankflow.accounts.api.service.BaasWebhookCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/baas/accounts")
public class BaasWebhookController {

    private final AccountService accountService;

    public BaasWebhookController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<Void> handle(@Valid @RequestBody BaasAccountWebhookRequest request) {
        accountService.activate(BaasWebhookCommand.from(request));
        return ResponseEntity.noContent().build();
    }
}

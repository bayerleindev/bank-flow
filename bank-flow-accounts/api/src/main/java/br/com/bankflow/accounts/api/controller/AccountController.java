package br.com.bankflow.accounts.api.controller;

import br.com.bankflow.accounts.api.dto.request.CreateAccountRequest;
import br.com.bankflow.accounts.api.dto.response.AccountCreatedResponse;
import br.com.bankflow.accounts.api.dto.response.AccountResponse;
import br.com.bankflow.accounts.api.service.AccountService;
import br.com.bankflow.accounts.api.service.CreateAccountCommand;
import br.com.bankflow.accounts.shared.domain.Account;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountCreatedResponse> create(
            @Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(CreateAccountCommand.from(request));

        return ResponseEntity.created(URI.create("/accounts/" + account.id()))
                .body(AccountCreatedResponse.from(account));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> findById(@PathVariable UUID accountId) {
        Account account = accountService.findById(accountId);
        return ResponseEntity.ok(AccountResponse.from(account));
    }
}

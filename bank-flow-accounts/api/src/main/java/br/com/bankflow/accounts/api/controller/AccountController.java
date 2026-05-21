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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    public ResponseEntity<AccountResponse> findById(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        requireSameAccount(jwt, accountId);
        Account account = accountService.findById(accountId);
        return ResponseEntity.ok(AccountResponse.from(account));
    }

    private static void requireSameAccount(Jwt jwt, UUID accountId) {
        UUID authenticatedAccountId = accountId(jwt);
        if (!accountId.equals(authenticatedAccountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account_forbidden");
        }
    }

    private static UUID accountId(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getClaimAsString("account_id"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_token");
        }

        try {
            return UUID.fromString(jwt.getClaimAsString("account_id"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_token", exception);
        }
    }
}

package br.com.bankflow.balance.api.controller;

import br.com.bankflow.balance.api.dto.response.BalanceResponse;
import br.com.bankflow.balance.api.service.BalanceService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/balances")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<List<BalanceResponse>> findByAccountId(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        requireSameAccount(jwt, accountId);
        return ResponseEntity.ok(
                balanceService.findByAccountId(accountId).stream()
                        .map(BalanceResponse::from)
                        .toList());
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

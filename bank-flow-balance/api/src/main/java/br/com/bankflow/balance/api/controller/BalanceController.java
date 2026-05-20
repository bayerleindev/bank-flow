package br.com.bankflow.balance.api.controller;

import br.com.bankflow.balance.api.dto.response.BalanceResponse;
import br.com.bankflow.balance.api.service.BalanceService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/balances")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<List<BalanceResponse>> findByAccountId(@PathVariable UUID accountId) {
        return ResponseEntity.ok(
                balanceService.findByAccountId(accountId).stream()
                        .map(BalanceResponse::from)
                        .toList());
    }
}

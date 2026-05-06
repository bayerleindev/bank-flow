package br.com.bankflow.balance.controllers;

import br.com.bankflow.balance.controllers.dtos.BalanceResponse;
import br.com.bankflow.balance.controllers.dtos.StatementResponse;
import br.com.bankflow.balance.services.BalanceQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BalanceController {
	private final BalanceQueryService balanceQueryService;

	public BalanceController(BalanceQueryService balanceQueryService) {
		this.balanceQueryService = balanceQueryService;
	}

	@GetMapping("/balances/{account_id}")
	public ResponseEntity<BalanceResponse> getBalance(@PathVariable("account_id") long accountId) {
		return ResponseEntity.ok(BalanceResponse.from(balanceQueryService.getBalance(accountId)));
	}

	@GetMapping("/balances/{account_id}/statement")
	public ResponseEntity<StatementResponse> getStatement(
			@PathVariable("account_id") long accountId,
			@RequestParam(value = "limit", required = false) Integer limit,
			@RequestParam(value = "cursor", required = false) String cursor
	) {
		return ResponseEntity.ok(StatementResponse.from(balanceQueryService.getStatement(accountId, limit, cursor)));
	}
}

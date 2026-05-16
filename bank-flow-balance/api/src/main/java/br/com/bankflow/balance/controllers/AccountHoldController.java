package br.com.bankflow.balance.controllers;

import br.com.bankflow.balance.controllers.dtos.AccountHoldResponse;
import br.com.bankflow.balance.controllers.dtos.CreateAccountHoldRequest;
import br.com.bankflow.balance.services.AccountHoldService;
import io.micrometer.observation.annotation.Observed;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountHoldController {
	private final AccountHoldService accountHoldService;

	public AccountHoldController(AccountHoldService accountHoldService) {
		this.accountHoldService = accountHoldService;
	}

	@PostMapping("/holds")
	public ResponseEntity<AccountHoldResponse> createHold(@RequestBody CreateAccountHoldRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(AccountHoldResponse.from(accountHoldService.createHold(request.toCommand())));
	}

	@PostMapping("/holds/{hold_id}/capture")
	public ResponseEntity<AccountHoldResponse> captureHold(@PathVariable("hold_id") String holdId) {
		return ResponseEntity.ok(AccountHoldResponse.from(accountHoldService.captureHold(holdId)));
	}

	@PostMapping("/holds/{hold_id}/release")
	public ResponseEntity<AccountHoldResponse> releaseHold(@PathVariable("hold_id") String holdId) {
		return ResponseEntity.ok(AccountHoldResponse.from(accountHoldService.releaseHold(holdId)));
	}
}

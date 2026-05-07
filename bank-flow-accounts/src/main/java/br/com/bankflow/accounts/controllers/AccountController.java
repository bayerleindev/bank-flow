package br.com.bankflow.accounts.controllers;

import br.com.bankflow.accounts.controllers.dtos.AccountResponse;
import br.com.bankflow.accounts.controllers.dtos.CreateAccountRequest;
import br.com.bankflow.accounts.domain.Account;
import br.com.bankflow.accounts.domain.AccountStatus;
import br.com.bankflow.accounts.services.AccountCreationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AccountController {
	private final AccountCreationService accountCreationService;

	public AccountController(AccountCreationService accountCreationService) {
		this.accountCreationService = accountCreationService;
	}

	@PostMapping("/accounts")
	public ResponseEntity<AccountResponse> createAccount(
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestBody CreateAccountRequest request
	) {
		Account account = accountCreationService.createAccount(request.toCommand(idempotencyKey));
		HttpStatus status = account.status() == AccountStatus.ACTIVE ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
		return ResponseEntity.status(status).body(AccountResponse.from(account));
	}

	@GetMapping("/accounts/{digital_account_id}")
	public ResponseEntity<AccountResponse> getAccount(@PathVariable("digital_account_id") UUID digitalAccountId) {
		return ResponseEntity.ok(AccountResponse.from(accountCreationService.getAccount(digitalAccountId)));
	}
}

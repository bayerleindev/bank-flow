package br.com.bankflow.balance.controllers;

import br.com.bankflow.balance.services.BalanceNotFoundException;
import br.com.bankflow.balance.services.AccountHoldNotFoundException;
import br.com.bankflow.balance.services.AccountHoldStateException;
import br.com.bankflow.balance.services.InsufficientFundsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(BalanceNotFoundException.class)
	ResponseEntity<ProblemDetail> handleBalanceNotFound(BalanceNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setTitle("Balance not found");
		problem.setDetail("No balance projection exists for account_id=%d".formatted(exception.accountId()));
		problem.setProperty("account_id", exception.accountId());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
	}

	@ExceptionHandler(AccountHoldNotFoundException.class)
	ResponseEntity<ProblemDetail> handleHoldNotFound(AccountHoldNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setTitle("Hold not found");
		problem.setDetail("No hold exists for hold_id=%s".formatted(exception.holdId()));
		problem.setProperty("hold_id", exception.holdId());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
	}

	@ExceptionHandler(InsufficientFundsException.class)
	ResponseEntity<ProblemDetail> handleInsufficientFunds(InsufficientFundsException exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		problem.setTitle("Insufficient funds");
		problem.setDetail("Available balance is lower than requested amount");
		problem.setProperty("account_id", exception.accountId());
		problem.setProperty("amount_minor", exception.amountMinor());
		problem.setProperty("currency", exception.currency());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	@ExceptionHandler(AccountHoldStateException.class)
	ResponseEntity<ProblemDetail> handleHoldState(AccountHoldStateException exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		problem.setTitle("Hold state conflict");
		problem.setDetail("Hold cannot be changed from current status");
		problem.setProperty("hold_id", exception.holdId());
		problem.setProperty("status", exception.status().name());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	@ExceptionHandler({
			IllegalArgumentException.class,
			MethodArgumentTypeMismatchException.class,
			MethodArgumentNotValidException.class
	})
	ResponseEntity<ProblemDetail> handleBadRequest(Exception exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setTitle("Invalid request");
		problem.setDetail(exception.getMessage());
		return ResponseEntity.badRequest().body(problem);
	}
}

package br.com.bankflow.balance.controllers;

import br.com.bankflow.balance.services.BalanceNotFoundException;
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

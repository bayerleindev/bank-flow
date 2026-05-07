package br.com.bankflow.accounts.controllers;

import br.com.bankflow.accounts.services.AccountNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(AccountNotFoundException.class)
	ResponseEntity<ProblemDetail> handleNotFound(AccountNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setTitle("Account not found");
		problem.setDetail("No account exists for identifier=%s".formatted(exception.identifier()));
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
	}

	@ExceptionHandler({
			IllegalArgumentException.class,
			MethodArgumentTypeMismatchException.class
	})
	ResponseEntity<ProblemDetail> handleBadRequest(Exception exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setTitle("Invalid request");
		problem.setDetail(exception.getMessage());
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(RestClientResponseException.class)
	ResponseEntity<ProblemDetail> handleDownstream(RestClientResponseException exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
		problem.setTitle("BaaS dependency failed");
		problem.setDetail(exception.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
	}
}

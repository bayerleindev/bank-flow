package br.com.bankflow.transfer.controllers;

import br.com.bankflow.transfer.services.TransferNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(TransferNotFoundException.class)
	ResponseEntity<ProblemDetail> handleNotFound(TransferNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setTitle("Transfer not found");
		problem.setDetail("No transfer exists for identifier=%s".formatted(exception.identifier()));
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

	@ExceptionHandler(HttpClientErrorException.Conflict.class)
	ResponseEntity<ProblemDetail> handleConflict(HttpClientErrorException.Conflict exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		problem.setTitle("Transfer rejected");
		problem.setDetail("Transfer could not be accepted by a downstream dependency");
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	@ExceptionHandler(RestClientResponseException.class)
	ResponseEntity<ProblemDetail> handleDownstream(RestClientResponseException exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
		problem.setTitle("Downstream dependency failed");
		problem.setDetail(exception.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
	}

	@ExceptionHandler({
			CallNotPermittedException.class,
			ResourceAccessException.class
	})
	ResponseEntity<ProblemDetail> handleDownstreamUnavailable(RuntimeException exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
		problem.setTitle("Downstream dependency unavailable");
		problem.setDetail(exception.getMessage());
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
	}
}

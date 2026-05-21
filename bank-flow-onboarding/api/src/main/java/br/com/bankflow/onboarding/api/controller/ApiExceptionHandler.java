package br.com.bankflow.onboarding.api.controller;

import br.com.bankflow.onboarding.api.dto.ApiErrorResponse;
import br.com.bankflow.onboarding.api.service.OnboardingException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OnboardingException.class)
    public ResponseEntity<ApiErrorResponse> handleOnboarding(OnboardingException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiErrorResponse(exception.code()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation() {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("invalid_request"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateKey() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("application_already_exists"));
    }
}

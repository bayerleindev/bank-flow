package br.com.bankflow.auth.controller;

import br.com.bankflow.auth.dto.ApiErrorResponse;
import br.com.bankflow.auth.service.AuthException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String INVALID_ISSUER_KEY = "invalid_issuer_key";
    private static final String INVALID_CREDENTIALS = "invalid_credentials";
    private static final String ACCOUNT_NOT_ACTIVE = "account_not_active";

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException exception) {
        if (INVALID_ISSUER_KEY.equals(exception.code())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse(exception.code()));
        }
        if (INVALID_CREDENTIALS.equals(exception.code())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse(exception.code()));
        }
        if (ACCOUNT_NOT_ACTIVE.equals(exception.code())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new ApiErrorResponse(exception.code()));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(exception.code()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation() {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("invalid_request"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateKey() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("credentials_already_exist"));
    }
}

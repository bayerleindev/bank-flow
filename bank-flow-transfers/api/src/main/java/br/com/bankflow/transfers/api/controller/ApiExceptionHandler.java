package br.com.bankflow.transfers.api.controller;

import br.com.bankflow.transfers.api.dto.response.ApiErrorResponse;
import br.com.bankflow.transfers.api.service.PixKeyValidationException;
import br.com.bankflow.transfers.api.service.TransferAuthenticationException;
import br.com.bankflow.transfers.api.service.TransferNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(WebClientResponseException.NotFound.class)
    public ResponseEntity<ApiErrorResponse> handlePixKeyNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("pix_key_not_found"));
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTransferNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("transfer_not_found"));
    }

    @ExceptionHandler(TransferAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleTransferAuthentication() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("invalid_token"));
    }

    @ExceptionHandler(PixKeyValidationException.class)
    public ResponseEntity<ApiErrorResponse> handlePixKeyValidation(
            PixKeyValidationException exception) {
        return ResponseEntity.unprocessableEntity().body(new ApiErrorResponse(exception.code()));
    }

    @ExceptionHandler({WebClientRequestException.class, WebClientResponseException.class})
    public ResponseEntity<ApiErrorResponse> handleBaasFailure() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("baas_unavailable"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateTransfer() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("duplicate_idempotency_key"));
    }
}

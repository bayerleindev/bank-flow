package br.com.bankflow.transfers.api.service;

public class TransferAuthenticationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TransferAuthenticationException() {
        super("invalid_token");
    }

    public TransferAuthenticationException(Throwable cause) {
        super("invalid_token", cause);
    }
}

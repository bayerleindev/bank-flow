package br.com.bankflow.transfers.api.service;

public class PixKeyValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public PixKeyValidationException(String code) {
        super(code);
        errorCode = code;
    }

    public PixKeyValidationException(String code, Throwable cause) {
        super(code, cause);
        errorCode = code;
    }

    public String code() {
        return errorCode;
    }
}

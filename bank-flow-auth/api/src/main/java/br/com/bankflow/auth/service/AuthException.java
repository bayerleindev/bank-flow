package br.com.bankflow.auth.service;

public class AuthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public AuthException(String code) {
        super(code);
        errorCode = code;
    }

    public AuthException(String code, Throwable cause) {
        super(code, cause);
        errorCode = code;
    }

    public String code() {
        return errorCode;
    }
}

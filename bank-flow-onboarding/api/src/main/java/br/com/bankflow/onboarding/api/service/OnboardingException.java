package br.com.bankflow.onboarding.api.service;

import org.springframework.http.HttpStatus;

public class OnboardingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private final HttpStatus httpStatus;

    public OnboardingException(HttpStatus status, String code) {
        super(code);
        httpStatus = status;
        errorCode = code;
    }

    public String code() {
        return errorCode;
    }

    public HttpStatus status() {
        return httpStatus;
    }
}

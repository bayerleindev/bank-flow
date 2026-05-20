package br.com.bankflow.auth.dto;

public record CreateTokenResponse(String accessToken, String tokenType, long expiresIn) {}

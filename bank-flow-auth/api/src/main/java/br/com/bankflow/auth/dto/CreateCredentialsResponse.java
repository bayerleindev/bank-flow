package br.com.bankflow.auth.dto;

import java.util.UUID;

public record CreateCredentialsResponse(UUID id, String documentNumber) {}

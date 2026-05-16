package br.com.bankflow.transfer.services;

import java.io.Serial;

public class TransferNotFoundException extends RuntimeException {
	private final String identifierString;
    @Serial
    private static final long serialVersionUID = -7034897190745766939L;

	public TransferNotFoundException(String identifierString) {
		super("transfer not found identifier=%s".formatted(identifierString));
		this.identifierString = identifierString;
	}

	public String identifier() {
		return identifierString;
	}
}

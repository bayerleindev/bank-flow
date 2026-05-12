package br.com.bankflow.transfer.services;

public class TransferNotFoundException extends RuntimeException {
	private final String identifier;

	public TransferNotFoundException(String identifier) {
		super("transfer not found identifier=%s".formatted(identifier));
		this.identifier = identifier;
	}

	public String identifier() {
		return identifier;
	}
}

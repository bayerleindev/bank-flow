package br.com.bankflow.ledger.repositories;

public class ImmuDbPersistenceException extends RuntimeException {
	public ImmuDbPersistenceException(String message, Throwable cause) {
		super(message, cause);
	}
}

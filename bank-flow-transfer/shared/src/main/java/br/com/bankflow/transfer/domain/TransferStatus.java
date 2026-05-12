package br.com.bankflow.transfer.domain;

public enum TransferStatus {
	RECEIVED,
	HOLD_CREATED,
	PSP_PENDING,
	PSP_CONFIRMED,
	POSTING_REQUESTED,
	COMPLETED,
	FAILED,
	EXPIRED,
	REVERSED;

	public boolean isTerminal() {
		return this == COMPLETED || this == FAILED || this == EXPIRED || this == REVERSED;
	}
}

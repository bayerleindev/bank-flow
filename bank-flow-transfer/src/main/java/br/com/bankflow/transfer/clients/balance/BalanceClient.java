package br.com.bankflow.transfer.clients.balance;

public interface BalanceClient {
	BalanceHoldResponse createHold(CreateBalanceHoldRequest request);

	void captureHold(String holdId);

	void releaseHold(String holdId);
}

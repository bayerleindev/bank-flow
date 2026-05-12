package br.com.bankflow.transfer.clients.psp;

import br.com.bankflow.transfer.domain.Transfer;

public interface PspClient {
	PspPaymentResponse createPayment(Transfer transfer);
}

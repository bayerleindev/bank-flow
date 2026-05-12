package br.com.bankflow.transfer.clients.psp;

import br.com.bankflow.transfer.domain.Transfer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank-flow.psp.mode", havingValue = "mock", matchIfMissing = true)
public class MockPspClient implements PspClient {
	@Override
	public PspPaymentResponse createPayment(Transfer transfer) {
		return new PspPaymentResponse(
				"psp-" + transfer.transferId(),
				PspPaymentStatus.PENDING,
				null
		);
	}
}

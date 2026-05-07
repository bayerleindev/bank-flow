package br.com.bankflow.transfer.controllers;

import br.com.bankflow.transfer.controllers.dtos.ExternalInboundTransferWebhookRequest;
import br.com.bankflow.transfer.controllers.dtos.TransferResponse;
import br.com.bankflow.transfer.services.TransferOrchestrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExternalInboundTransferWebhookController {
	private final TransferOrchestrationService transferOrchestrationService;

	public ExternalInboundTransferWebhookController(TransferOrchestrationService transferOrchestrationService) {
		this.transferOrchestrationService = transferOrchestrationService;
	}

	@PostMapping("/webhooks/external-institutions/transfers")
	public ResponseEntity<TransferResponse> receiveTransfer(@RequestBody ExternalInboundTransferWebhookRequest request) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(TransferResponse.from(transferOrchestrationService.receiveExternalInboundTransfer(request.toCommand())));
	}
}

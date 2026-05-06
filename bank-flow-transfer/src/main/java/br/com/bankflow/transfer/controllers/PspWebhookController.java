package br.com.bankflow.transfer.controllers;

import br.com.bankflow.transfer.controllers.dtos.PspWebhookRequest;
import br.com.bankflow.transfer.controllers.dtos.TransferResponse;
import br.com.bankflow.transfer.services.TransferOrchestrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PspWebhookController {
	private final TransferOrchestrationService transferOrchestrationService;

	public PspWebhookController(TransferOrchestrationService transferOrchestrationService) {
		this.transferOrchestrationService = transferOrchestrationService;
	}

	@PostMapping("/webhooks/psp/transfers")
	public ResponseEntity<TransferResponse> handleWebhook(@RequestBody PspWebhookRequest request) {
		return ResponseEntity.ok(TransferResponse.from(transferOrchestrationService.handlePspWebhook(request.toCommand())));
	}
}

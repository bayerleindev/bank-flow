package br.com.bankflow.transfer.controllers;

import br.com.bankflow.transfer.controllers.dtos.CreateTransferRequest;
import br.com.bankflow.transfer.controllers.dtos.TransferResponse;
import br.com.bankflow.transfer.services.TransferOrchestrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class TransferController {
	private final TransferOrchestrationService transferOrchestrationService;

	public TransferController(TransferOrchestrationService transferOrchestrationService) {
		this.transferOrchestrationService = transferOrchestrationService;
	}

	@PostMapping("/transfers")
	public ResponseEntity<TransferResponse> createTransfer(
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestBody CreateTransferRequest request
	) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(TransferResponse.from(transferOrchestrationService.createTransfer(request.toCommand(idempotencyKey))));
	}

	@GetMapping("/transfers/{transfer_id}")
	public ResponseEntity<TransferResponse> getTransfer(@PathVariable("transfer_id") UUID transferId) {
		return ResponseEntity.ok(TransferResponse.from(transferOrchestrationService.getTransfer(transferId)));
	}
}

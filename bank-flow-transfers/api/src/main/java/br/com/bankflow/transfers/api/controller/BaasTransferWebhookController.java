package br.com.bankflow.transfers.api.controller;

import br.com.bankflow.transfers.api.dto.request.BaasTransferWebhookRequest;
import br.com.bankflow.transfers.api.service.BaasTransferWebhookService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/baas/webhooks/transfers")
public class BaasTransferWebhookController {

    private final BaasTransferWebhookService baasTransferWebhookService;

    public BaasTransferWebhookController(BaasTransferWebhookService baasTransferWebhookService) {
        this.baasTransferWebhookService = baasTransferWebhookService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@Valid @RequestBody BaasTransferWebhookRequest request) {
        baasTransferWebhookService.handle(request);
        return ResponseEntity.accepted().build();
    }
}

package br.com.bankflow.transfers.api.controller;

import br.com.bankflow.transfers.api.dto.request.CreateTransferRequest;
import br.com.bankflow.transfers.api.dto.response.CreateTransferResponse;
import br.com.bankflow.transfers.api.dto.response.TransferResponse;
import br.com.bankflow.transfers.api.service.TransferAuthenticationException;
import br.com.bankflow.transfers.api.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/transfers")
public class TransferController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<CreateTransferResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        CreateTransferResponse response =
                CreateTransferResponse.from(
                        transferService.create(request.toCommand(idempotencyKey, accountId(jwt))));
        return ResponseEntity.created(URI.create("/transfers/" + response.id())).body(response);
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponse> findById(@PathVariable UUID transferId) {
        return ResponseEntity.ok(TransferResponse.from(transferService.findById(transferId)));
    }

    private static UUID accountId(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getClaimAsString("account_id"))) {
            throw new TransferAuthenticationException();
        }

        try {
            return UUID.fromString(jwt.getClaimAsString("account_id"));
        } catch (IllegalArgumentException exception) {
            throw new TransferAuthenticationException(exception);
        }
    }
}

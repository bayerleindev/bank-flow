package br.com.bankflow.transfers.api.controller;

import br.com.bankflow.transfers.api.dto.response.PixKeyResponse;
import br.com.bankflow.transfers.api.service.PixKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/keys")
public class PixKeyController {

    private final PixKeyService pixKeyService;

    public PixKeyController(PixKeyService pixKeyService) {
        this.pixKeyService = pixKeyService;
    }

    @GetMapping("/{key}")
    public ResponseEntity<PixKeyResponse> findByKey(@PathVariable String key) {
        return ResponseEntity.ok(pixKeyService.findByKey(key));
    }
}

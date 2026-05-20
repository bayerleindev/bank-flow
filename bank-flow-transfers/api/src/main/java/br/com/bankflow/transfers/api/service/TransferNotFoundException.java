package br.com.bankflow.transfers.api.service;

import java.util.UUID;

public class TransferNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TransferNotFoundException(UUID transferId) {
        super("Transfer not found: " + transferId);
    }
}

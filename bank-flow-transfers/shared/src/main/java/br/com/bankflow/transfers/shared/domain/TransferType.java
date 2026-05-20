package br.com.bankflow.transfers.shared.domain;

public enum TransferType {
    PIX;

    public String requestedStatus() {
        return name() + "_REQUESTED";
    }
}

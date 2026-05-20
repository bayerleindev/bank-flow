package br.com.bankflow.accounts.shared.kafka;

public record TransferParty(String bank, String account, String branch) {}

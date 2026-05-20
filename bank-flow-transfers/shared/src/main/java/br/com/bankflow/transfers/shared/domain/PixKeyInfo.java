package br.com.bankflow.transfers.shared.domain;

public record PixKeyInfo(PixAccount account, PixOwner owner, String endToEndId) {}

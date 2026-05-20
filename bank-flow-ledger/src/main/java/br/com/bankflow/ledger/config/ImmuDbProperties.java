package br.com.bankflow.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.immudb")
public record ImmuDbProperties(
        String host, int port, String username, String password, String database) {}

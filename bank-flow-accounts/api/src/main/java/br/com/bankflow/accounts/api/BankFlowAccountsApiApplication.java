package br.com.bankflow.accounts.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "br.com.bankflow.accounts")
public class BankFlowAccountsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankFlowAccountsApiApplication.class, args);
    }
}

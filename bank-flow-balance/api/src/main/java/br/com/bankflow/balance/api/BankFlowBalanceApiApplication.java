package br.com.bankflow.balance.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "br.com.bankflow.balance")
public class BankFlowBalanceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankFlowBalanceApiApplication.class, args);
    }
}

package br.com.bankflow.accounts.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "br.com.bankflow.accounts")
public class BankFlowAccountsWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankFlowAccountsWorkerApplication.class, args);
    }
}

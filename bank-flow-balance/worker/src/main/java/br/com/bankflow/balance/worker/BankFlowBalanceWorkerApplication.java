package br.com.bankflow.balance.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "br.com.bankflow.balance")
public class BankFlowBalanceWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankFlowBalanceWorkerApplication.class, args);
    }
}

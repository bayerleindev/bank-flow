package br.com.bankflow.transfers.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "br.com.bankflow.transfers")
public class BankFlowTransfersApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankFlowTransfersApiApplication.class, args);
    }
}

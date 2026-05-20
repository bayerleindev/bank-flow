package br.com.bankflow.transfers.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
@ComponentScan(basePackages = "br.com.bankflow.transfers")
public class BankFlowTransfersWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankFlowTransfersWorkerApplication.class, args);
    }
}

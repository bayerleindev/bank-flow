package br.com.bankflow.auth.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication(scanBasePackages = "br.com.bankflow.auth")
public class BankFlowAuthWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankFlowAuthWorkerApplication.class, args);
    }
}

package br.com.bankflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BankFlowTransferApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankFlowTransferApplication.class, args);
	}

}

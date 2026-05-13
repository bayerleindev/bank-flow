package br.com.bankflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BankFlowOutboxerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankFlowOutboxerApplication.class, args);
	}

}

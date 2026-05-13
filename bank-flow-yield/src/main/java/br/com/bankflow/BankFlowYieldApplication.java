package br.com.bankflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BankFlowYieldApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankFlowYieldApplication.class, args);
	}

}

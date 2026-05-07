package br.com.bankflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BankFlowBalanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankFlowBalanceApplication.class, args);
	}

}

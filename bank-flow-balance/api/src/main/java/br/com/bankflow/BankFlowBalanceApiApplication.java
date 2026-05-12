package br.com.bankflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "br.com.bankflow")
public class BankFlowBalanceApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankFlowBalanceApiApplication.class, args);
	}

}

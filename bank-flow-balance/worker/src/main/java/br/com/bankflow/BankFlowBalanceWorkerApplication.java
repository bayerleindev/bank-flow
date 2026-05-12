package br.com.bankflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "br.com.bankflow")
public class BankFlowBalanceWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankFlowBalanceWorkerApplication.class, args);
	}

}

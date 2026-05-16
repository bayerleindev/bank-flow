package br.com.bankflow;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(basePackages = "br.com.bankflow.transfer.repositories")
public class BankFlowTransferSharedTestApplication {
}

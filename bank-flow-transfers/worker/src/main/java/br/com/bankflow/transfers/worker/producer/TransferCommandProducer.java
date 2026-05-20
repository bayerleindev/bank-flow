package br.com.bankflow.transfers.worker.producer;

import br.com.bankflow.transfers.shared.kafka.AccountValidateCommand;
import br.com.bankflow.transfers.shared.kafka.BalanceCaptureCommand;
import br.com.bankflow.transfers.shared.kafka.BalanceHoldCommand;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransferCommandProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String accountValidateCommandTopic;
    private final String balanceCaptureCommandTopic;
    private final String balanceHoldCommandTopic;

    public TransferCommandProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.account-validate-command}")
                    String accountValidateCommandTopic,
            @Value("${app.kafka.topics.balance-capture-command}") String balanceCaptureCommandTopic,
            @Value("${app.kafka.topics.balance-hold-command}") String balanceHoldCommandTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.accountValidateCommandTopic = accountValidateCommandTopic;
        this.balanceCaptureCommandTopic = balanceCaptureCommandTopic;
        this.balanceHoldCommandTopic = balanceHoldCommandTopic;
    }

    @Retry(name = "commandPublish")
    @CircuitBreaker(name = "commandPublish")
    public void publishAccountValidate(AccountValidateCommand command) {
        kafkaTemplate
                .send(accountValidateCommandTopic, command.transferId().toString(), command)
                .join();
    }

    @Retry(name = "commandPublish")
    @CircuitBreaker(name = "commandPublish")
    public void publishBalanceHold(BalanceHoldCommand command) {
        kafkaTemplate
                .send(balanceHoldCommandTopic, command.transferId().toString(), command)
                .join();
    }

    @Retry(name = "commandPublish")
    @CircuitBreaker(name = "commandPublish")
    public void publishBalanceCapture(BalanceCaptureCommand command) {
        kafkaTemplate
                .send(balanceCaptureCommandTopic, command.transferId().toString(), command)
                .join();
    }
}

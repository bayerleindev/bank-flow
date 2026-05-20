package br.com.bankflow.transfers.api.producer;

import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.kafka.BalanceCaptureCommand;
import br.com.bankflow.transfers.shared.kafka.BalanceReleaseCommand;
import br.com.bankflow.transfers.shared.kafka.MovementCreatedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransferSettlementProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String balanceCaptureCommandTopic;
    private final String balanceReleaseCommandTopic;
    private final String movementCreatedTopic;
    private final UUID externalSettlementAccountId;

    public TransferSettlementProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.balance-capture-command}") String balanceCaptureCommandTopic,
            @Value("${app.kafka.topics.balance-release-command}") String balanceReleaseCommandTopic,
            @Value("${app.kafka.topics.movement-created}") String movementCreatedTopic,
            @Value("${app.ledger.external-settlement-account-id}")
                    UUID externalSettlementAccountId) {
        this.kafkaTemplate = kafkaTemplate;
        this.balanceCaptureCommandTopic = balanceCaptureCommandTopic;
        this.balanceReleaseCommandTopic = balanceReleaseCommandTopic;
        this.movementCreatedTopic = movementCreatedTopic;
        this.externalSettlementAccountId = externalSettlementAccountId;
    }

    @Retry(name = "kafkaPublish")
    @CircuitBreaker(name = "kafkaPublish")
    public void publishSuccessfulSettlement(Transfer transfer, Instant requestedAt) {
        MovementCreatedEvent event =
                new MovementCreatedEvent(
                        movementId(transfer),
                        transfer.id(),
                        transfer.debitAccountId(),
                        creditAccountId(transfer),
                        transfer.amountMinor(),
                        transfer.currency(),
                        transfer.type().name(),
                        requestedAt);
        kafkaTemplate.send(movementCreatedTopic, event.movementId().toString(), event).join();

        BalanceCaptureCommand command =
                new BalanceCaptureCommand(
                        transfer.id(),
                        transfer.debitAccountId(),
                        transfer.amountMinor(),
                        transfer.currency(),
                        requestedAt);
        kafkaTemplate
                .send(balanceCaptureCommandTopic, command.transferId().toString(), command)
                .join();
    }

    @Retry(name = "kafkaPublish")
    @CircuitBreaker(name = "kafkaPublish")
    public void publishRejectedSettlement(Transfer transfer, Instant requestedAt) {
        BalanceReleaseCommand command =
                new BalanceReleaseCommand(
                        transfer.id(),
                        transfer.debitAccountId(),
                        transfer.amountMinor(),
                        transfer.currency(),
                        requestedAt);
        kafkaTemplate
                .send(balanceReleaseCommandTopic, command.transferId().toString(), command)
                .join();
    }

    private static UUID movementId(Transfer transfer) {
        return UUID.nameUUIDFromBytes(
                ("movement:" + transfer.id()).getBytes(StandardCharsets.UTF_8));
    }

    private UUID creditAccountId(Transfer transfer) {
        if (transfer.creditAccountId() != null) {
            return transfer.creditAccountId();
        }
        return externalSettlementAccountId;
    }
}

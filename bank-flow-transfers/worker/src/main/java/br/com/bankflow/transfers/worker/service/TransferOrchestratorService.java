package br.com.bankflow.transfers.worker.service;

import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.kafka.AccountValidateCommand;
import br.com.bankflow.transfers.shared.kafka.AccountValidatedEvent;
import br.com.bankflow.transfers.shared.kafka.AccountValidationStatus;
import br.com.bankflow.transfers.shared.kafka.BalanceCaptureCommand;
import br.com.bankflow.transfers.shared.kafka.BalanceHeldEvent;
import br.com.bankflow.transfers.shared.kafka.BalanceHoldCommand;
import br.com.bankflow.transfers.shared.kafka.BalanceHoldStatus;
import br.com.bankflow.transfers.shared.kafka.TransferRequestedEvent;
import br.com.bankflow.transfers.shared.repository.TransferRepository;
import br.com.bankflow.transfers.worker.client.BaasTransferClient;
import br.com.bankflow.transfers.worker.producer.MovementEventProducer;
import br.com.bankflow.transfers.worker.producer.TransferCommandProducer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("PMD.TooManyMethods")
public class TransferOrchestratorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferOrchestratorService.class);
    private static final String ACCOUNT_VALIDATION_REQUESTED_STATUS =
            "ACCOUNT_VALIDATION_REQUESTED";
    private static final String BAAS_REQUESTED_STATUS = "BAAS_REQUESTED";
    private static final String BALANCE_HELD_STATUS = "BALANCE_HELD";
    private static final String BALANCE_HOLD_REQUESTED_STATUS = "BALANCE_HOLD_REQUESTED";
    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final String BANK_FLOW_ISPB = "13935893";
    private static final String PROCESSING_STATUS = "PROCESSING";
    private static final String TRANSFER_NOT_FOUND_REASON = "transfer_not_found";

    private final TransferRepository transferRepository;
    private final TransferCommandProducer transferCommandProducer;
    private final MovementEventProducer movementEventProducer;
    private final BaasTransferClient baasTransferClient;
    private final Clock clock;

    public TransferOrchestratorService(
            TransferRepository transferRepository,
            TransferCommandProducer transferCommandProducer,
            MovementEventProducer movementEventProducer,
            BaasTransferClient baasTransferClient,
            Clock clock) {
        this.transferRepository = transferRepository;
        this.transferCommandProducer = transferCommandProducer;
        this.movementEventProducer = movementEventProducer;
        this.baasTransferClient = baasTransferClient;
        this.clock = clock;
    }

    @Retry(name = "transferRequestedConsumer")
    @CircuitBreaker(name = "transferRequestedConsumer")
    @Transactional
    public void startProcessing(TransferRequestedEvent event) {
        Instant now = Instant.now(clock);
        boolean transitioned =
                transferRepository.updateStatus(
                        event.id(), event.type().requestedStatus(), PROCESSING_STATUS, now);

        if (!transitioned) {
            discardUnprocessableEvent(event);
            return;
        }

        transferCommandProducer.publishAccountValidate(toAccountValidateCommand(event, now));
        requestAccountValidation(event, now);
        LOGGER.info("account validation requested transferId={}", event.id());
    }

    @Retry(name = "transferRequestedConsumer")
    @CircuitBreaker(name = "transferRequestedConsumer")
    @Transactional
    public void handleAccountValidated(AccountValidatedEvent event) {
        if (event.status() == AccountValidationStatus.REJECTED) {
            rejectTransfer(event);
            return;
        }

        requestBalanceHold(event);
    }

    @Retry(name = "transferRequestedConsumer")
    @CircuitBreaker(name = "transferRequestedConsumer")
    @Transactional
    public void handleBalanceHeld(BalanceHeldEvent event) {
        if (event.status() == BalanceHoldStatus.REJECTED) {
            rejectTransfer(event);
            return;
        }

        handleHeldBalance(event);
    }

    private void requestBalanceHold(AccountValidatedEvent event) {
        Instant now = Instant.now(clock);
        Transfer transfer = transferRepository.findById(event.transferId()).orElse(null);

        if (transfer == null) {
            LOGGER.warn(
                    "account.validated.event discarded because transfer was not found transferId={}",
                    event.transferId());
            return;
        }

        if (!ACCOUNT_VALIDATION_REQUESTED_STATUS.equals(transfer.status())) {
            LOGGER.info(
                    "account.validated.event discarded because transfer is not in account validation status transferId={} currentStatus={}",
                    event.transferId(),
                    transfer.status());
            return;
        }

        boolean accountIdsUpdated =
                transferRepository.updateValidatedAccountIds(
                        event.transferId(), event.debitAccountId(), event.creditAccountId(), now);

        if (!accountIdsUpdated) {
            throw new IllegalStateException("Transfer account ids could not be updated");
        }

        transfer =
                transferRepository
                        .findById(event.transferId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Transfer was not found after account id update"));

        transferCommandProducer.publishBalanceHold(toBalanceHoldCommand(transfer, event, now));
        boolean transitioned =
                transferRepository.updateStatus(
                        event.transferId(),
                        ACCOUNT_VALIDATION_REQUESTED_STATUS,
                        BALANCE_HOLD_REQUESTED_STATUS,
                        now);

        if (!transitioned) {
            throw new IllegalStateException(
                    "Transfer could not move to " + BALANCE_HOLD_REQUESTED_STATUS);
        }

        LOGGER.info("balance hold requested transferId={}", event.transferId());
    }

    private void handleHeldBalance(BalanceHeldEvent event) {
        Transfer transfer = transferRepository.findById(event.transferId()).orElse(null);

        if (transfer == null) {
            LOGGER.warn(
                    "balance.held.event discarded because transfer was not found transferId={}",
                    event.transferId());
            return;
        }

        if (!BALANCE_HOLD_REQUESTED_STATUS.equals(transfer.status())) {
            LOGGER.info(
                    "balance.held.event discarded because transfer is not in balance hold requested status transferId={} currentStatus={}",
                    event.transferId(),
                    transfer.status());
            return;
        }

        if (BANK_FLOW_ISPB.equals(transfer.creditParty().bank())) {
            movementEventProducer.publishCreated(transfer, event.heldAt());
            transferCommandProducer.publishBalanceCapture(
                    toBalanceCaptureCommand(transfer, event.heldAt()));
            completeInternalTransfer(event);
            return;
        }

        baasTransferClient.requestPixPayment(transfer, event.heldAt());
        requestBaasTransfer(event);
    }

    private void completeInternalTransfer(BalanceHeldEvent event) {
        boolean transitioned =
                transferRepository.updateStatus(
                        event.transferId(),
                        BALANCE_HOLD_REQUESTED_STATUS,
                        COMPLETED_STATUS,
                        event.heldAt());

        if (!transitioned) {
            throw new IllegalStateException("Transfer could not move to " + COMPLETED_STATUS);
        }

        LOGGER.info("internal transfer completed transferId={}", event.transferId());
    }

    private void requestBaasTransfer(BalanceHeldEvent event) {
        boolean transitioned =
                transferRepository.updateStatus(
                        event.transferId(),
                        BALANCE_HOLD_REQUESTED_STATUS,
                        BAAS_REQUESTED_STATUS,
                        event.heldAt());

        if (!transitioned) {
            throw new IllegalStateException("Transfer could not move to " + BAAS_REQUESTED_STATUS);
        }

        LOGGER.info("baas transfer requested transferId={}", event.transferId());
    }

    private void rejectTransfer(AccountValidatedEvent event) {
        boolean transitioned =
                transferRepository.reject(
                        event.transferId(),
                        ACCOUNT_VALIDATION_REQUESTED_STATUS,
                        event.reason(),
                        Instant.now(clock));

        if (transitioned) {
            LOGGER.info(
                    "transfer rejected by account validation transferId={} reason={}",
                    event.transferId(),
                    event.reason());
            return;
        }

        if (!transferRepository.existsById(event.transferId())) {
            LOGGER.warn(
                    "account.validated.event rejected discarded because transfer was not found transferId={}",
                    event.transferId());
            return;
        }

        LOGGER.info(
                "account.validated.event rejected discarded because transfer is not in account validation status transferId={}",
                event.transferId());
    }

    private void rejectTransfer(BalanceHeldEvent event) {
        boolean transitioned =
                transferRepository.reject(
                        event.transferId(),
                        BALANCE_HOLD_REQUESTED_STATUS,
                        event.reason(),
                        event.heldAt());

        if (transitioned) {
            LOGGER.info(
                    "transfer rejected by balance hold transferId={} reason={}",
                    event.transferId(),
                    event.reason());
            return;
        }

        discardBalanceHeldEvent(event);
    }

    private void discardBalanceHeldEvent(BalanceHeldEvent event) {
        if (!transferRepository.existsById(event.transferId())) {
            LOGGER.warn(
                    "balance.held.event discarded because transfer was not found transferId={}",
                    event.transferId());
            return;
        }

        LOGGER.info(
                "balance.held.event discarded because transfer is not in balance hold requested status transferId={}",
                event.transferId());
    }

    private void requestAccountValidation(TransferRequestedEvent event, Instant now) {
        boolean transitioned =
                transferRepository.updateStatus(
                        event.id(), PROCESSING_STATUS, ACCOUNT_VALIDATION_REQUESTED_STATUS, now);

        if (!transitioned) {
            throw new IllegalStateException(
                    "Transfer could not move to " + ACCOUNT_VALIDATION_REQUESTED_STATUS);
        }
    }

    private void discardUnprocessableEvent(TransferRequestedEvent event) {
        if (!transferRepository.existsById(event.id())) {
            LOGGER.warn(
                    "transfer.requested discarded because transfer was not found transferId={}",
                    event.id());
            return;
        }

        LOGGER.info(
                "transfer.requested discarded because transfer is not in requested status transferId={} expectedStatus={}",
                event.id(),
                event.type().requestedStatus());
    }

    private static AccountValidateCommand toAccountValidateCommand(
            TransferRequestedEvent event, Instant requestedAt) {
        return new AccountValidateCommand(
                event.id(),
                event.debitParty(),
                event.creditParty(),
                event.idempotencyKey(),
                requestedAt);
    }

    private static BalanceHoldCommand toBalanceHoldCommand(
            Transfer transfer, AccountValidatedEvent event, Instant requestedAt) {
        return new BalanceHoldCommand(
                transfer.id(),
                event.debitAccountId(),
                transfer.debitParty(),
                transfer.amountMinor(),
                transfer.currency(),
                transfer.idempotencyKey(),
                requestedAt);
    }

    private static BalanceCaptureCommand toBalanceCaptureCommand(
            Transfer transfer, Instant requestedAt) {
        return new BalanceCaptureCommand(
                transfer.id(),
                transfer.debitAccountId(),
                transfer.amountMinor(),
                transfer.currency(),
                requestedAt);
    }
}

package br.com.bankflow.transfers.worker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import br.com.bankflow.transfers.shared.kafka.AccountValidateCommand;
import br.com.bankflow.transfers.shared.kafka.AccountValidatedEvent;
import br.com.bankflow.transfers.shared.kafka.AccountValidationStatus;
import br.com.bankflow.transfers.shared.kafka.BalanceHeldEvent;
import br.com.bankflow.transfers.shared.kafka.BalanceHoldCommand;
import br.com.bankflow.transfers.shared.kafka.BalanceHoldStatus;
import br.com.bankflow.transfers.shared.kafka.TransferRequestedEvent;
import br.com.bankflow.transfers.shared.repository.TransferRepository;
import br.com.bankflow.transfers.worker.client.BaasTransferClient;
import br.com.bankflow.transfers.worker.producer.MovementEventProducer;
import br.com.bankflow.transfers.worker.producer.TransferCommandProducer;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class TransferOrchestratorServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-20T12:00:00Z");
    private static final UUID TRANSFER_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID DEBIT_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CREDIT_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final TransferParty DEBIT_PARTY =
            new TransferParty("13935893", "10000-1", "0001");
    private static final TransferParty EXTERNAL_CREDIT_PARTY =
            new TransferParty("260", "12345-6", "0001");

    @Mock private TransferRepository transferRepository;
    @Mock private TransferCommandProducer transferCommandProducer;
    @Mock private MovementEventProducer movementEventProducer;
    @Mock private BaasTransferClient baasTransferClient;

    @Test
    void shouldRequestAccountValidationUsingDebitAccountId() {
        TransferOrchestratorService service = service();
        TransferRequestedEvent event =
                new TransferRequestedEvent(
                        TRANSFER_ID,
                        DEBIT_ACCOUNT_ID,
                        EXTERNAL_CREDIT_PARTY,
                        "E2E123",
                        1000,
                        "pix",
                        "BRL",
                        TransferType.PIX,
                        "PIX_REQUESTED",
                        NOW);
        when(transferRepository.updateStatus(TRANSFER_ID, "PIX_REQUESTED", "PROCESSING", NOW))
                .thenReturn(true);
        when(transferRepository.updateStatus(
                        TRANSFER_ID, "PROCESSING", "ACCOUNT_VALIDATION_REQUESTED", NOW))
                .thenReturn(true);

        service.startProcessing(event);

        ArgumentCaptor<AccountValidateCommand> commandCaptor =
                ArgumentCaptor.forClass(AccountValidateCommand.class);
        verify(transferCommandProducer).publishAccountValidate(commandCaptor.capture());
        assertThat(commandCaptor.getValue().debitAccountId()).isEqualTo(DEBIT_ACCOUNT_ID);
        assertThat(commandCaptor.getValue().creditParty()).isEqualTo(EXTERNAL_CREDIT_PARTY);
    }

    @Test
    void shouldUpdateDebitPartyFromAccountValidatedEvent() {
        TransferOrchestratorService service = service();
        Transfer beforeValidation = transfer("ACCOUNT_VALIDATION_REQUESTED", null, null);
        Transfer afterValidation =
                transfer("ACCOUNT_VALIDATION_REQUESTED", DEBIT_PARTY, CREDIT_ACCOUNT_ID);
        when(transferRepository.findById(TRANSFER_ID))
                .thenReturn(Optional.of(beforeValidation))
                .thenReturn(Optional.of(afterValidation));
        when(transferRepository.updateValidatedAccounts(
                        TRANSFER_ID, DEBIT_ACCOUNT_ID, DEBIT_PARTY, CREDIT_ACCOUNT_ID, NOW))
                .thenReturn(true);
        when(transferRepository.updateStatus(
                        TRANSFER_ID, "ACCOUNT_VALIDATION_REQUESTED", "BALANCE_HOLD_REQUESTED", NOW))
                .thenReturn(true);

        service.handleAccountValidated(approvedEvent());

        verify(transferRepository)
                .updateValidatedAccounts(
                        TRANSFER_ID, DEBIT_ACCOUNT_ID, DEBIT_PARTY, CREDIT_ACCOUNT_ID, NOW);
    }

    @Test
    void shouldRequestBalanceHoldAfterAccountValidation() {
        TransferOrchestratorService service = service();
        Transfer beforeValidation = transfer("ACCOUNT_VALIDATION_REQUESTED", null, null);
        Transfer afterValidation =
                transfer("ACCOUNT_VALIDATION_REQUESTED", DEBIT_PARTY, CREDIT_ACCOUNT_ID);
        when(transferRepository.findById(TRANSFER_ID))
                .thenReturn(Optional.of(beforeValidation))
                .thenReturn(Optional.of(afterValidation));
        when(transferRepository.updateValidatedAccounts(
                        TRANSFER_ID, DEBIT_ACCOUNT_ID, DEBIT_PARTY, CREDIT_ACCOUNT_ID, NOW))
                .thenReturn(true);
        when(transferRepository.updateStatus(
                        TRANSFER_ID, "ACCOUNT_VALIDATION_REQUESTED", "BALANCE_HOLD_REQUESTED", NOW))
                .thenReturn(true);

        service.handleAccountValidated(approvedEvent());

        ArgumentCaptor<BalanceHoldCommand> commandCaptor =
                ArgumentCaptor.forClass(BalanceHoldCommand.class);
        verify(transferCommandProducer).publishBalanceHold(commandCaptor.capture());
        assertThat(commandCaptor.getValue().debitAccountId()).isEqualTo(DEBIT_ACCOUNT_ID);
        assertThat(commandCaptor.getValue().debitParty()).isEqualTo(DEBIT_PARTY);
    }

    @Test
    void shouldRejectTransferWhenAccountValidationIsRejected() {
        TransferOrchestratorService service = service();
        AccountValidatedEvent event =
                new AccountValidatedEvent(
                        TRANSFER_ID,
                        AccountValidationStatus.REJECTED,
                        "debit_account_not_found_or_inactive",
                        null,
                        null,
                        null,
                        NOW);
        when(transferRepository.reject(
                        TRANSFER_ID,
                        "ACCOUNT_VALIDATION_REQUESTED",
                        "debit_account_not_found_or_inactive",
                        NOW))
                .thenReturn(true);

        service.handleAccountValidated(event);

        verify(transferRepository)
                .reject(
                        TRANSFER_ID,
                        "ACCOUNT_VALIDATION_REQUESTED",
                        "debit_account_not_found_or_inactive",
                        NOW);
        verify(transferCommandProducer, never()).publishBalanceHold(org.mockito.Mockito.any());
    }

    @Test
    void shouldSendDebitPartyToBaasWhenExternalTransferIsHeld() {
        TransferOrchestratorService service = service();
        Transfer transfer = transfer("BALANCE_HOLD_REQUESTED", DEBIT_PARTY, null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(transferRepository.updateStatus(
                        TRANSFER_ID, "BALANCE_HOLD_REQUESTED", "BAAS_REQUESTED", NOW))
                .thenReturn(true);

        service.handleBalanceHeld(heldEvent());

        verify(baasTransferClient).requestPixPayment(transfer, NOW);
        verify(transferRepository)
                .updateStatus(TRANSFER_ID, "BALANCE_HOLD_REQUESTED", "BAAS_REQUESTED", NOW);
    }

    @Test
    void shouldNotOpenGlobalConsumerCircuitWhenBaasFails() throws NoSuchMethodException {
        Method method =
                TransferOrchestratorService.class.getMethod(
                        "handleBalanceHeld", BalanceHeldEvent.class);

        assertThat(
                        method.getAnnotation(
                                io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
                                        .class))
                .isNull();
    }

    private TransferOrchestratorService service() {
        return new TransferOrchestratorService(
                transferRepository,
                transferCommandProducer,
                movementEventProducer,
                baasTransferClient,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AccountValidatedEvent approvedEvent() {
        return new AccountValidatedEvent(
                TRANSFER_ID,
                AccountValidationStatus.APPROVED,
                null,
                DEBIT_ACCOUNT_ID,
                DEBIT_PARTY,
                CREDIT_ACCOUNT_ID,
                NOW);
    }

    private static BalanceHeldEvent heldEvent() {
        return new BalanceHeldEvent(
                TRANSFER_ID, BalanceHoldStatus.HELD, null, DEBIT_ACCOUNT_ID, 1000, "BRL", NOW);
    }

    private static Transfer transfer(
            String status, TransferParty debitParty, UUID creditAccountId) {
        Instant createdAt = Instant.parse("2026-05-20T11:59:00Z");
        return new Transfer(
                TRANSFER_ID,
                debitParty,
                EXTERNAL_CREDIT_PARTY,
                "E2E123",
                1000,
                "pix",
                "BRL",
                TransferType.PIX,
                status,
                null,
                DEBIT_ACCOUNT_ID,
                creditAccountId,
                createdAt,
                createdAt);
    }
}

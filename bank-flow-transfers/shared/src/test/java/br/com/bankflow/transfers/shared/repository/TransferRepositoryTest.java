package br.com.bankflow.transfers.shared.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"PMD.TooManyStaticImports", "PMD.UnitTestContainsTooManyAsserts"})
class TransferRepositoryTest {

    private static final UUID TRANSFER_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID DEBIT_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CREDIT_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final Instant NOW = Instant.parse("2026-05-20T12:00:00Z");

    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistTransferWithDebitAccountIdAndNullDebitPartyBeforeValidation() {
        TransferRepository repository = new TransferRepository(jdbcTemplate);

        repository.save(transfer(null));

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(any(String.class), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        assertThat(args[1]).isNull();
        assertThat(args[2]).isNull();
        assertThat(args[3]).isNull();
        assertThat(args[14]).isEqualTo(DEBIT_ACCOUNT_ID);
    }

    @Test
    void shouldPersistValidatedDebitPartySnapshot() {
        TransferRepository repository = new TransferRepository(jdbcTemplate);
        TransferParty debitParty = new TransferParty("13935893", "10000-1", "0001");
        when(jdbcTemplate.update(
                        any(String.class),
                        eq(DEBIT_ACCOUNT_ID),
                        eq("13935893"),
                        eq("10000-1"),
                        eq("0001"),
                        eq(CREDIT_ACCOUNT_ID),
                        eq(Timestamp.from(NOW)),
                        eq(TRANSFER_ID)))
                .thenReturn(1);

        boolean updated =
                repository.updateValidatedAccounts(
                        TRANSFER_ID, DEBIT_ACCOUNT_ID, debitParty, CREDIT_ACCOUNT_ID, NOW);

        assertThat(updated).isTrue();
    }

    @Test
    void shouldNotOverwriteTransferWhenStatusTransitionIsInvalid() {
        TransferRepository repository = new TransferRepository(jdbcTemplate);
        when(jdbcTemplate.update(
                        any(String.class),
                        eq("COMPLETED"),
                        eq(Timestamp.from(NOW)),
                        eq(TRANSFER_ID),
                        eq("BALANCE_HOLD_REQUESTED")))
                .thenReturn(0);

        boolean updated =
                repository.updateStatus(TRANSFER_ID, "BALANCE_HOLD_REQUESTED", "COMPLETED", NOW);

        assertThat(updated).isFalse();
    }

    private static Transfer transfer(TransferParty debitParty) {
        return new Transfer(
                TRANSFER_ID,
                debitParty,
                new TransferParty("260", "12345-6", "0001"),
                "E2E123",
                1000,
                "pix",
                "BRL",
                TransferType.PIX,
                "PIX_REQUESTED",
                null,
                DEBIT_ACCOUNT_ID,
                null,
                NOW,
                NOW);
    }
}

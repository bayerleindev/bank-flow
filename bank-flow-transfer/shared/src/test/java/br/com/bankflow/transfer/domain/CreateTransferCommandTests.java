package br.com.bankflow.transfer.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CreateTransferCommandTests {

    @Test
    void validationFailsWhenIdempotencyKeyIsMissing() {
        CreateTransferCommand command = new CreateTransferCommand(
                null, UUID.randomUUID(), UUID.randomUUID(), 100, "BRL", "test"
        );
        assertThrows(IllegalArgumentException.class, command::validate);
    }

    @Test
    void validationFailsWhenSourceDigitalAccountIdIsMissing() {
        CreateTransferCommand command = new CreateTransferCommand(
                "key", null, UUID.randomUUID(), 100, "BRL", "test"
        );
        assertThrows(IllegalArgumentException.class, command::validate);
    }

    @Test
    void validationFailsWhenDestinationDigitalAccountIdIsMissing() {
        CreateTransferCommand command = new CreateTransferCommand(
                "key", UUID.randomUUID(), null, 100, "BRL", "test"
        );
        assertThrows(IllegalArgumentException.class, command::validate);
    }

    @Test
    void validationFailsWhenSourceAndDestinationAreSame() {
        UUID accountId = UUID.randomUUID();
        CreateTransferCommand command = new CreateTransferCommand(
                "key", accountId, accountId, 100, "BRL", "test"
        );
        assertThrows(IllegalArgumentException.class, command::validate);
    }

    @Test
    void validationFailsWhenAmountIsNotPositive() {
        CreateTransferCommand command = new CreateTransferCommand(
                "key", UUID.randomUUID(), UUID.randomUUID(), 0, "BRL", "test"
        );
        assertThrows(IllegalArgumentException.class, command::validate);
    }

    @Test
    void validationFailsWhenCurrencyIsInvalid() {
        CreateTransferCommand command = new CreateTransferCommand(
                "key", UUID.randomUUID(), UUID.randomUUID(), 100, "BR", "test"
        );
        assertThrows(IllegalArgumentException.class, command::validate);
    }

    @Test
    void validationFailsWhenDescriptionIsMissing() {
        CreateTransferCommand command = new CreateTransferCommand(
                "key", UUID.randomUUID(), UUID.randomUUID(), 100, "BRL", ""
        );
        assertThrows(IllegalArgumentException.class, command::validate);
    }

    @Test
    void validationSucceedsForValidCommand() {
        CreateTransferCommand command = new CreateTransferCommand(
                "key", UUID.randomUUID(), UUID.randomUUID(), 100, "BRL", "test"
        );
        assertDoesNotThrow(command::validate);
    }
}

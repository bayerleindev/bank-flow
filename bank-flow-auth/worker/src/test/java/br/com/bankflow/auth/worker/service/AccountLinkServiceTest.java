package br.com.bankflow.auth.worker.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import br.com.bankflow.auth.kafka.AccountCreatedEvent;
import br.com.bankflow.auth.repository.AccountLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-21T12:00:00Z");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock private AccountLinkRepository accountLinkRepository;

    @Test
    void shouldLinkAccountCreatedEventByDocumentNumber() {
        AccountLinkService service =
                new AccountLinkService(accountLinkRepository, Clock.fixed(NOW, ZoneOffset.UTC));

        service.link(new AccountCreatedEvent(ACCOUNT_ID, "12345678900", "0001", "12345", "6"));

        verify(accountLinkRepository)
                .upsert(
                        argThat(
                                link ->
                                        "12345678900".equals(link.documentNumber())
                                                && ACCOUNT_ID.equals(link.accountId())
                                                && "0001".equals(link.branchNumber())
                                                && "12345".equals(link.accountNumber())
                                                && "6".equals(link.accountDigit())
                                                && NOW.equals(link.createdAt())
                                                && NOW.equals(link.updatedAt())));
    }
}

package br.com.bankflow.accounts.worker.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.bankflow.accounts.shared.domain.Account;
import br.com.bankflow.accounts.shared.domain.AccountStatus;
import br.com.bankflow.accounts.shared.kafka.AccountCreationRequestedEvent;
import br.com.bankflow.accounts.shared.repository.AccountRepository;
import br.com.bankflow.accounts.worker.producer.AccountRequestedProducer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class AccountCreationRequestedServiceTest {

    private static final UUID ONBOARDING_APPLICATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CREDENTIALS_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Mock private AccountRepository accountRepository;
    @Mock private AccountRequestedProducer accountRequestedProducer;

    @Test
    void shouldCreateAccountAndPublishAccountRequestedWhenNoAccountExists() {
        AccountCreationRequestedService service =
                new AccountCreationRequestedService(accountRepository, accountRequestedProducer);
        when(accountRepository.findByOnboardingApplicationId(ONBOARDING_APPLICATION_ID))
                .thenReturn(Optional.empty());
        when(accountRepository.findByDocumentNumber("12345678900")).thenReturn(Optional.empty());
        when(accountRepository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.process(event());

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).create(accountCaptor.capture());
        verify(accountRequestedProducer).publish(accountCaptor.getValue());
        org.assertj.core.api.Assertions.assertThat(
                        accountCaptor.getValue().onboardingApplicationId())
                .isEqualTo(ONBOARDING_APPLICATION_ID);
        org.assertj.core.api.Assertions.assertThat(accountCaptor.getValue().credentialsId())
                .isEqualTo(CREDENTIALS_ID);
        org.assertj.core.api.Assertions.assertThat(accountCaptor.getValue().status())
                .isEqualTo(AccountStatus.CREATION_REQUESTED);
    }

    @Test
    void shouldPublishExistingCreationRequestedAccountWithoutCreatingDuplicate() {
        AccountCreationRequestedService service =
                new AccountCreationRequestedService(accountRepository, accountRequestedProducer);
        Account existing = account(AccountStatus.CREATION_REQUESTED);
        when(accountRepository.findByOnboardingApplicationId(ONBOARDING_APPLICATION_ID))
                .thenReturn(Optional.of(existing));

        service.process(event());

        verify(accountRepository, never()).create(any());
        verify(accountRequestedProducer).publish(existing);
    }

    @Test
    void shouldIgnoreExistingAccountThatAlreadyMovedForward() {
        AccountCreationRequestedService service =
                new AccountCreationRequestedService(accountRepository, accountRequestedProducer);
        when(accountRepository.findByOnboardingApplicationId(ONBOARDING_APPLICATION_ID))
                .thenReturn(Optional.of(account(AccountStatus.PENDING_BAAS)));

        service.process(event());

        verify(accountRepository, never()).create(any());
        verify(accountRequestedProducer, never()).publish(any());
    }

    private AccountCreationRequestedEvent event() {
        return new AccountCreationRequestedEvent(
                ONBOARDING_APPLICATION_ID,
                CREDENTIALS_ID,
                "Ada Lovelace",
                "12345678900",
                "ada@example.com",
                "Anne",
                null,
                "+5511999999999",
                LocalDate.parse("1815-12-10"),
                "Rua Um",
                false,
                Instant.parse("2026-05-21T12:00:00Z"));
    }

    private Account account(AccountStatus status) {
        return new Account(
                UUID.fromString("00000000-0000-0000-0000-000000000303"),
                "Ada Lovelace",
                "12345678900",
                "ada@example.com",
                "Anne",
                null,
                "+5511999999999",
                LocalDate.parse("1815-12-10"),
                "Rua Um",
                false,
                status,
                null,
                null,
                null,
                null,
                ONBOARDING_APPLICATION_ID,
                CREDENTIALS_ID,
                Instant.parse("2026-05-21T12:00:00Z"),
                Instant.parse("2026-05-21T12:00:00Z"));
    }
}

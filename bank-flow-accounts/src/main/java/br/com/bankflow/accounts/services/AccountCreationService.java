package br.com.bankflow.accounts.services;

import br.com.bankflow.accounts.clients.baas.BaasAccountResponse;
import br.com.bankflow.accounts.clients.baas.BaasAccountStatus;
import br.com.bankflow.accounts.clients.baas.BaasClient;
import br.com.bankflow.accounts.domain.Account;
import br.com.bankflow.accounts.domain.AccountCreatedEvent;
import br.com.bankflow.accounts.domain.AccountStatus;
import br.com.bankflow.accounts.domain.CreateAccountCommand;
import br.com.bankflow.accounts.domain.OutboxEvent;
import br.com.bankflow.accounts.repositories.AccountRepository;
import br.com.bankflow.accounts.repositories.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AccountCreationService {
	private static final String ACCOUNT_CREATED_EVENT = "account.created";

	private final AccountRepository accountRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final BaasClient baasClient;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final String accountCreatedTopic;

	public AccountCreationService(
			AccountRepository accountRepository,
			OutboxEventRepository outboxEventRepository,
			BaasClient baasClient,
			ObjectMapper objectMapper,
			Clock clock,
			@Value("${bank-flow.kafka.topics.account-created}") String accountCreatedTopic
	) {
		this.accountRepository = accountRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.baasClient = baasClient;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.accountCreatedTopic = accountCreatedTopic;
	}

	@Transactional
	public Account createAccount(CreateAccountCommand command) {
		command.validate();
		return accountRepository.findByIdempotencyKey(command.idempotencyKey())
				.or(() -> accountRepository.findByDocumentNumber(command.documentNumber()))
				.orElseGet(() -> createNewAccount(command));
	}

	public Account getAccount(UUID accountId) {
		return accountRepository.findByAccountId(accountId)
				.orElseThrow(() -> new AccountNotFoundException(accountId.toString()));
	}

	protected Account createNewAccount(CreateAccountCommand command) {
		long now = clock.millis();
		Account account = accountRepository.create(UUID.randomUUID(), UUID.randomUUID(), command, now);
		BaasAccountResponse response = baasClient.createAccount(command);
		Account updated = accountRepository.updateFromBaas(
				account.accountId(),
				response,
				toAccountStatus(response.status()),
				response.failureReason(),
				clock.millis()
		);
		if (updated.status() == AccountStatus.ACTIVE) {
			createAccountCreatedOutbox(updated);
		}
		return updated;
	}

	private AccountStatus toAccountStatus(BaasAccountStatus status) {
		if (status == null) {
			throw new IllegalArgumentException("BaaS status is required");
		}
		return switch (status) {
			case ACTIVE -> AccountStatus.ACTIVE;
			case PENDING -> AccountStatus.BAAS_PENDING;
			case REJECTED -> AccountStatus.REJECTED;
		};
	}

	private void createAccountCreatedOutbox(Account account) {
		try {
			long now = clock.millis();
			outboxEventRepository.createIfAbsent(new OutboxEvent(
					UUID.randomUUID(),
					"Account",
					account.accountId().toString(),
					ACCOUNT_CREATED_EVENT,
					accountCreatedTopic,
					account.ownerId().toString(),
					objectMapper.writeValueAsString(AccountCreatedEvent.from(account)),
					"PENDING",
					0,
					null,
					now,
					null
			));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to serialize account-created event", exception);
		}
	}
}

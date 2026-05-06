package br.com.bankflow.balance.services;

import br.com.bankflow.balance.domain.AccountHold;
import br.com.bankflow.balance.domain.AccountHoldStatus;
import br.com.bankflow.balance.domain.CreateAccountHoldCommand;
import br.com.bankflow.balance.repositories.AccountHoldRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AccountHoldService {
	private final AccountHoldRepository accountHoldRepository;
	private final Clock clock;

	public AccountHoldService(AccountHoldRepository accountHoldRepository, Clock clock) {
		this.accountHoldRepository = accountHoldRepository;
		this.clock = clock;
	}

	@Transactional
	public AccountHold createHold(CreateAccountHoldCommand command) {
		long now = clock.millis();
		command.validate(now);
		return accountHoldRepository.findByTransferId(command.transferId())
				.orElseGet(() -> createNewHold(command, now));
	}

	@Transactional
	public AccountHold captureHold(String holdId) {
		return closeHold(holdId, AccountHoldStatus.CAPTURED);
	}

	@Transactional
	public AccountHold releaseHold(String holdId) {
		return closeHold(holdId, AccountHoldStatus.RELEASED);
	}

	private AccountHold createNewHold(CreateAccountHoldCommand command, long now) {
		String holdId = UUID.randomUUID().toString();
		AccountHold hold;
		try {
			hold = accountHoldRepository.createHeld(holdId, command, now);
		} catch (DuplicateKeyException exception) {
			return accountHoldRepository.findByTransferId(command.transferId())
					.orElseThrow(() -> exception);
		}
		boolean reserved = accountHoldRepository.reserveBalance(
				command.accountId(),
				command.currency(),
				command.amountMinor(),
				now
		);
		if (!reserved) {
			throw new InsufficientFundsException(command.accountId(), command.amountMinor(), command.currency());
		}
		return hold;
	}

	private AccountHold closeHold(String holdId, AccountHoldStatus targetStatus) {
		if (holdId == null || holdId.isBlank()) {
			throw new IllegalArgumentException("hold_id is required");
		}
		AccountHold hold = accountHoldRepository.findByHoldId(holdId)
				.orElseThrow(() -> new AccountHoldNotFoundException(holdId));
		if (hold.status() != AccountHoldStatus.HELD) {
			throw new AccountHoldStateException(hold.holdId(), hold.status());
		}
		boolean closed = targetStatus == AccountHoldStatus.CAPTURED
				? accountHoldRepository.captureHeld(holdId, clock.millis())
				: accountHoldRepository.releaseHeld(holdId, clock.millis());
		if (!closed) {
			throw new AccountHoldStateException(hold.holdId(), hold.status());
		}
		return accountHoldRepository.findByHoldId(holdId)
				.orElseThrow(() -> new AccountHoldNotFoundException(holdId));
	}
}

package br.com.bankflow.balance.services;

import br.com.bankflow.balance.domain.AccountHold;
import br.com.bankflow.balance.domain.AccountHoldStatus;
import br.com.bankflow.balance.domain.CreateAccountHoldCommand;
import br.com.bankflow.balance.observability.BalanceMetrics;
import br.com.bankflow.balance.repositories.AccountHoldRepository;
import io.micrometer.observation.annotation.Observed;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AccountHoldService {
	private final AccountHoldRepository accountHoldRepository;
	private final BalanceMetrics balanceMetrics;
	private final Clock clock;

	public AccountHoldService(AccountHoldRepository accountHoldRepository, BalanceMetrics balanceMetrics, Clock clock) {
		this.accountHoldRepository = accountHoldRepository;
		this.balanceMetrics = balanceMetrics;
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
		return closeHold(holdId, AccountHoldStatus.CAPTURED, "capture");
	}

	@Transactional
	public AccountHold releaseHold(String holdId) {
		return closeHold(holdId, AccountHoldStatus.RELEASED, "release");
	}

	@Transactional
	@Scheduled(fixedDelayString = "${bank-flow.holds.expiration.fixed-delay-ms:30000}")
	public void expireHolds() {
		int expired = accountHoldRepository.expireHeld(clock.millis());
		balanceMetrics.recordHoldsExpired(expired);
	}

    @Observed(name = "new-transfer")
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
				command.digitalAccountId(),
				command.currency(),
				command.amountMinor(),
				now
		);
		if (!reserved) {
			throw new InsufficientFundsException(command.digitalAccountId(), command.amountMinor(), command.currency());
		}
		balanceMetrics.recordHoldCreated();
		return hold;
	}

	private AccountHold closeHold(String holdId, AccountHoldStatus targetStatus, String operation) {
		if (holdId == null || holdId.isBlank()) {
			balanceMetrics.recordHoldCloseFailure(operation, "missing_hold_id");
			throw new IllegalArgumentException("hold_id is required");
		}
		AccountHold hold = accountHoldRepository.findByHoldId(holdId)
				.orElseThrow(() -> {
					balanceMetrics.recordHoldCloseFailure(operation, "not_found");
					return new AccountHoldNotFoundException(holdId);
				});
		if (hold.status() == targetStatus) {
			return hold;
		}
		if (hold.status() != AccountHoldStatus.HELD) {
			balanceMetrics.recordHoldCloseFailure(operation, "invalid_state");
			throw new AccountHoldStateException(hold.holdId(), hold.status());
		}
		boolean closed = targetStatus == AccountHoldStatus.CAPTURED
				? accountHoldRepository.captureHeld(holdId, clock.millis())
				: accountHoldRepository.releaseHeld(holdId, clock.millis());
		if (!closed) {
			balanceMetrics.recordHoldCloseFailure(operation, "close_failed");
			throw new AccountHoldStateException(hold.holdId(), hold.status());
		}
		AccountHold closedHold = accountHoldRepository.findByHoldId(holdId)
				.orElseThrow(() -> new AccountHoldNotFoundException(holdId));
		if (targetStatus == AccountHoldStatus.CAPTURED) {
			balanceMetrics.recordHoldCaptured();
		} else if (targetStatus == AccountHoldStatus.RELEASED) {
			balanceMetrics.recordHoldReleased();
		}
		return closedHold;
	}
}

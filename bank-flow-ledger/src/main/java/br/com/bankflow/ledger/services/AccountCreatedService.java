package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.AccountCreatedEvent;
import br.com.bankflow.ledger.domain.LedgerAccount;
import br.com.bankflow.ledger.repositories.LedgerAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class AccountCreatedService {
	private static final Logger log = LoggerFactory.getLogger(AccountCreatedService.class);

	private final LedgerAccountRepository ledgerAccountRepository;
	private final NumericIdGenerator numericIdGenerator;
	private final Clock clock;

	public AccountCreatedService(
			LedgerAccountRepository ledgerAccountRepository,
			NumericIdGenerator numericIdGenerator,
			Clock clock
	) {
		this.ledgerAccountRepository = ledgerAccountRepository;
		this.numericIdGenerator = numericIdGenerator;
		this.clock = clock;
	}

	public void createLedgerAccount(AccountCreatedEvent event) {
		event.validate();
		LedgerAccount account = LedgerAccount.from(
				numericIdGenerator.nextAccountId(),
				clock.millis(),
				event
		);
		boolean created = ledgerAccountRepository.saveIfNotExists(account);
		if (!created) {
			log.info(
					"ledger account already exists accountCode={} digitalAccountId={} sourceAccount={} currency={}",
					account.accountCode(),
					event.digitalAccountId(),
					event.account(),
					event.currency()
			);
			return;
		}

		log.info(
				"ledger account created accountId={} accountCode={} digitalAccountId={} sourceAccount={} currency={}",
				account.accountId(),
				account.accountCode(),
				event.digitalAccountId(),
				event.account(),
				event.currency()
		);
	}
}

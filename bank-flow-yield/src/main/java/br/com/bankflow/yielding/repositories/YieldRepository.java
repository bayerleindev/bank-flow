package br.com.bankflow.yielding.repositories;

import br.com.bankflow.yielding.domain.AccountYieldAccrual;
import br.com.bankflow.yielding.domain.CdiRate;
import br.com.bankflow.yielding.domain.EligibleBalance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface YieldRepository {
	CdiRate saveRateIfAbsent(CdiRate rate);

	List<EligibleBalance> findEligibleBalances();

	Optional<AccountYieldAccrual> createAccrualIfAbsent(AccountYieldAccrual accrual);

	void markPostingRequested(UUID accrualId);

	boolean markPosted(UUID accrualId);

	boolean hasAccrualsFor(LocalDate referenceDate);

	long countAccrualsByStatus(String status);

	double oldestAccrualAgeSecondsByStatus(String status, long now);

	long countCdiRates();
}

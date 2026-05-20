package br.com.bankflow.balance.api.service;

import br.com.bankflow.balance.shared.domain.Balance;
import br.com.bankflow.balance.shared.repository.BalanceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BalanceService {

    private final BalanceRepository balanceRepository;

    public BalanceService(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    public List<Balance> findByAccountId(UUID accountId) {
        return balanceRepository.findByAccountId(accountId);
    }
}

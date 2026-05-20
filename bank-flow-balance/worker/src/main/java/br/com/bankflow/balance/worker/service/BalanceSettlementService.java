package br.com.bankflow.balance.worker.service;

import br.com.bankflow.balance.shared.kafka.BalanceCaptureCommand;
import br.com.bankflow.balance.shared.kafka.BalanceReleaseCommand;
import br.com.bankflow.balance.shared.repository.BalanceRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BalanceSettlementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BalanceSettlementService.class);

    private final BalanceRepository balanceRepository;
    private final Clock clock;

    public BalanceSettlementService(BalanceRepository balanceRepository, Clock clock) {
        this.balanceRepository = balanceRepository;
        this.clock = clock;
    }

    public void capture(BalanceCaptureCommand command) {
        boolean processed = balanceRepository.capture(command, Instant.now(clock));
        if (processed) {
            LOGGER.info("balance hold captured transferId={}", command.transferId());
            return;
        }
        LOGGER.info("balance capture ignored transferId={}", command.transferId());
    }

    public void release(BalanceReleaseCommand command) {
        boolean processed = balanceRepository.release(command, Instant.now(clock));
        if (processed) {
            LOGGER.info("balance hold released transferId={}", command.transferId());
            return;
        }
        LOGGER.info("balance release ignored transferId={}", command.transferId());
    }
}

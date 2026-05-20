package br.com.bankflow.balance.worker.service;

import br.com.bankflow.balance.shared.kafka.BalanceHeldEvent;
import br.com.bankflow.balance.shared.kafka.BalanceHoldCommand;
import br.com.bankflow.balance.shared.repository.BalanceRepository;
import br.com.bankflow.balance.worker.producer.BalanceHeldEventProducer;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BalanceHoldService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BalanceHoldService.class);

    private final BalanceRepository balanceRepository;
    private final BalanceHeldEventProducer balanceHeldEventProducer;
    private final Clock clock;

    public BalanceHoldService(
            BalanceRepository balanceRepository,
            BalanceHeldEventProducer balanceHeldEventProducer,
            Clock clock) {
        this.balanceRepository = balanceRepository;
        this.balanceHeldEventProducer = balanceHeldEventProducer;
        this.clock = clock;
    }

    public void hold(BalanceHoldCommand command) {
        BalanceHeldEvent event = balanceRepository.hold(command, Instant.now(clock));
        balanceHeldEventProducer.publish(event);
        LOGGER.info(
                "balance hold result published transferId={} status={} reason={}",
                event.transferId(),
                event.status(),
                event.reason());
    }
}

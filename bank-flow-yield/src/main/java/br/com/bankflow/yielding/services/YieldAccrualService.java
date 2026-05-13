package br.com.bankflow.yielding.services;

import br.com.bankflow.yielding.clients.bcb.BcbCdiClient;
import br.com.bankflow.yielding.domain.AccountYieldAccrual;
import br.com.bankflow.yielding.domain.CdiRate;
import br.com.bankflow.yielding.domain.EligibleBalance;
import br.com.bankflow.yielding.domain.OutboxEvent;
import br.com.bankflow.yielding.domain.YieldAccrualEvent;
import br.com.bankflow.yielding.repositories.OutboxEventRepository;
import br.com.bankflow.yielding.repositories.YieldRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Service
public class YieldAccrualService {
	private static final Logger log = LoggerFactory.getLogger(YieldAccrualService.class);
	private static final String EVENT_TYPE = "yield.cdi_accrued";
	private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

	private final BcbCdiClient cdiClient;
	private final YieldRepository yieldRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final ZoneId zoneId;
	private final String yieldAccrualsTopic;

	public YieldAccrualService(
			BcbCdiClient cdiClient,
			YieldRepository yieldRepository,
			OutboxEventRepository outboxEventRepository,
			ObjectMapper objectMapper,
			Clock clock,
			@Value("${bank-flow.yield.zone-id:America/Sao_Paulo}") String zoneId,
			@Value("${bank-flow.kafka.topics.yield-accruals}") String yieldAccrualsTopic
	) {
		this.cdiClient = cdiClient;
		this.yieldRepository = yieldRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.zoneId = ZoneId.of(zoneId);
		this.yieldAccrualsTopic = yieldAccrualsTopic;
	}

	@Scheduled(cron = "${bank-flow.yield.accrual.cron:0 0 3 * * *}", zone = "${bank-flow.yield.zone-id:America/Sao_Paulo}")
	public void accruePreviousDay() {
		accrue(LocalDate.now(clock.withZone(zoneId)).minusDays(1));
	}

	@Transactional
	public void accrue(LocalDate referenceDate) {
		if (yieldRepository.hasAccrualsFor(referenceDate)) {
			log.info("yield accrual already processed referenceDate={}", referenceDate);
			return;
		}
		CdiRate cdiRate = yieldRepository.saveRateIfAbsent(cdiClient.fetch(referenceDate));
		BigDecimal effectiveRatePercent = cdiRate.effectiveDailyFactor()
				.subtract(BigDecimal.ONE)
				.multiply(ONE_HUNDRED);
		int created = 0;
		for (EligibleBalance balance : yieldRepository.findEligibleBalances()) {
			long yieldAmountMinor = calculateYieldAmount(balance.postedMinor(), effectiveRatePercent);
			if (yieldAmountMinor <= 0) {
				continue;
			}
			AccountYieldAccrual accrual = new AccountYieldAccrual(
					UUID.randomUUID(),
					referenceDate,
					balance.digitalAccountId(),
					balance.postedMinor(),
					yieldAmountMinor,
					balance.currency(),
					cdiRate.cdiDailyRatePercent(),
					cdiRate.yieldCdiPercentage(),
					"PENDING",
					clock.millis()
			);
			Optional<AccountYieldAccrual> createdAccrual = yieldRepository.createAccrualIfAbsent(accrual);
			if (createdAccrual.isPresent()) {
				AccountYieldAccrual savedAccrual = createdAccrual.get();
				createOutbox(savedAccrual);
				yieldRepository.markPostingRequested(savedAccrual.accrualId());
				created++;
			}
		}
		log.info("yield accrual processed referenceDate={} accrualsCreated={}", referenceDate, created);
	}

	private long calculateYieldAmount(long postedMinor, BigDecimal effectiveRatePercent) {
		return BigDecimal.valueOf(postedMinor)
				.multiply(effectiveRatePercent)
				.divide(ONE_HUNDRED, 0, RoundingMode.HALF_UP)
				.longValue();
	}

	private void createOutbox(AccountYieldAccrual accrual) {
		try {
			long now = clock.millis();
			outboxEventRepository.createIfAbsent(new OutboxEvent(
					UUID.randomUUID(),
					"YieldAccrual",
					"yield:%s:%s".formatted(accrual.digitalAccountId(), accrual.referenceDate()),
					EVENT_TYPE,
					yieldAccrualsTopic,
					accrual.digitalAccountId().toString(),
					objectMapper.writeValueAsString(YieldAccrualEvent.from(accrual)),
					"PENDING",
					0,
					null,
					now,
					null
			));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to serialize yield accrual event", exception);
		}
	}
}

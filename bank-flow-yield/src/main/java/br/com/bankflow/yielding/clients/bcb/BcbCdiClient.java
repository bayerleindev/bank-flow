package br.com.bankflow.yielding.clients.bcb;

import br.com.bankflow.yielding.domain.CdiRate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Component
public class BcbCdiClient {
	private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
	private static final MathContext MATH_CONTEXT = new MathContext(18, RoundingMode.HALF_UP);

	private final RestClient restClient;
	private final Clock clock;
	private final String baseUrl;
	private final String source;
	private final BigDecimal yieldCdiPercentage;
	private final long lookbackDays;

	public BcbCdiClient(
			RestClient.Builder restClientBuilder,
			Clock clock,
			@Value("${bank-flow.yield.cdi.base-url}") String baseUrl,
			@Value("${bank-flow.yield.cdi.source}") String source,
			@Value("${bank-flow.yield.cdi.percentage}") BigDecimal yieldCdiPercentage,
			@Value("${bank-flow.yield.cdi.lookback-days:7}") long lookbackDays
	) {
		this.restClient = restClientBuilder.build();
		this.clock = clock;
		this.baseUrl = baseUrl;
		this.source = source;
		this.yieldCdiPercentage = yieldCdiPercentage;
		this.lookbackDays = lookbackDays;
	}

	public CdiRate fetch(LocalDate referenceDate) {
		LocalDate startDate = referenceDate.minusDays(lookbackDays);
		URI uri = UriComponentsBuilder.fromUriString(baseUrl)
				.queryParam("formato", "json")
				.queryParam("dataInicial", startDate.format(REQUEST_DATE_FORMATTER))
				.queryParam("dataFinal", referenceDate.format(REQUEST_DATE_FORMATTER))
				.build()
				.toUri();
		List<BcbCdiResponse> response = restClient.get()
				.uri(uri)
				.retrieve()
				.body(new org.springframework.core.ParameterizedTypeReference<>() {
				});
		if (response == null || response.isEmpty()) {
			throw new IllegalStateException("CDI rate not found for reference_date=%s".formatted(referenceDate));
		}
		BcbCdiResponse selectedRate = response.stream()
				.max(Comparator.comparing(rate -> LocalDate.parse(rate.data(), REQUEST_DATE_FORMATTER)))
				.orElseThrow();
		LocalDate sourceRateDate = LocalDate.parse(selectedRate.data(), REQUEST_DATE_FORMATTER);
		String rawValue = selectedRate.valor();
		BigDecimal cdiDailyRatePercent = new BigDecimal(rawValue.replace(',', '.'));
		BigDecimal cdiDailyFactor = BigDecimal.ONE.add(cdiDailyRatePercent.divide(ONE_HUNDRED, MATH_CONTEXT));
		BigDecimal effectiveRatePercent = cdiDailyRatePercent
				.multiply(yieldCdiPercentage, MATH_CONTEXT)
				.divide(ONE_HUNDRED, MATH_CONTEXT);
		BigDecimal effectiveDailyFactor = BigDecimal.ONE.add(effectiveRatePercent.divide(ONE_HUNDRED, MATH_CONTEXT));
		long now = clock.millis();
		return new CdiRate(
				referenceDate,
				sourceRateDate,
				source,
				uri.toString(),
				rawValue,
				cdiDailyRatePercent,
				cdiDailyFactor,
				yieldCdiPercentage,
				effectiveDailyFactor,
				now,
				now
		);
	}

	private record BcbCdiResponse(String data, String valor) {
	}
}

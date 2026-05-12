package br.com.bankflow.balance.services;

import br.com.bankflow.balance.domain.AccountBalance;
import br.com.bankflow.balance.domain.AccountStatementLine;
import br.com.bankflow.balance.repositories.BalanceQueryRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class BalanceQueryService {
	private static final int DEFAULT_STATEMENT_LIMIT = 50;
	private static final int MAX_STATEMENT_LIMIT = 200;
	private static final Pattern CURSOR_PATTERN = Pattern.compile("\\d+:\\d+");

	private final BalanceQueryRepository balanceQueryRepository;

	public BalanceQueryService(BalanceQueryRepository balanceQueryRepository) {
		this.balanceQueryRepository = balanceQueryRepository;
	}

	public AccountBalance getBalance(UUID digitalAccountId) {
		validateDigitalAccountId(digitalAccountId);
		return balanceQueryRepository.findBalance(digitalAccountId)
				.orElseThrow(() -> new BalanceNotFoundException(digitalAccountId));
	}

	public AccountStatement getStatement(UUID digitalAccountId, Integer requestedLimit, String cursorValue) {
		AccountBalance balance = getBalance(digitalAccountId);
		int limit = normalizeLimit(requestedLimit);
		BalanceQueryRepository.StatementCursor cursor = decodeCursor(cursorValue);
		List<AccountStatementLine> lines = balanceQueryRepository.findStatementLines(digitalAccountId, limit, cursor);
		String nextCursor = lines.size() == limit ? encodeCursor(lines.getLast()) : null;
		return new AccountStatement(balance, lines, limit, nextCursor);
	}

	private void validateDigitalAccountId(UUID digitalAccountId) {
		if (digitalAccountId == null) {
			throw new IllegalArgumentException("digital_account_id is required");
		}
	}

	private int normalizeLimit(Integer requestedLimit) {
		if (requestedLimit == null) {
			return DEFAULT_STATEMENT_LIMIT;
		}
		if (requestedLimit <= 0) {
			throw new IllegalArgumentException("limit must be positive");
		}
		return Math.min(requestedLimit, MAX_STATEMENT_LIMIT);
	}

	private BalanceQueryRepository.StatementCursor decodeCursor(String cursorValue) {
		if (cursorValue == null || cursorValue.isBlank()) {
			return null;
		}
		String decoded;
		try {
			decoded = new String(Base64.getUrlDecoder().decode(cursorValue), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("cursor is invalid");
		}
		if (!CURSOR_PATTERN.matcher(decoded).matches()) {
			throw new IllegalArgumentException("cursor is invalid");
		}
		String[] parts = decoded.split(":");
		long occurredAt = Long.parseLong(parts[0]);
		long lineId = Long.parseLong(parts[1]);
		if (occurredAt <= 0 || lineId <= 0) {
			throw new IllegalArgumentException("cursor is invalid");
		}
		return new BalanceQueryRepository.StatementCursor(occurredAt, lineId);
	}

	private String encodeCursor(AccountStatementLine line) {
		String rawCursor = "%d:%d".formatted(line.occurredAt(), line.lineId());
		return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
	}

	public record AccountStatement(
			AccountBalance balance,
			List<AccountStatementLine> lines,
			int limit,
			String nextCursor
	) {
	}
}

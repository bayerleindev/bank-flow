package br.com.bankflow.ledger.configs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import br.com.bankflow.ledger.repositories.LedgerAccountRepository;
import br.com.bankflow.ledger.repositories.LedgerPostingRepository;
import br.com.bankflow.ledger.services.LedgerPostingPublisher;

@Configuration
public class ImmuDbConfig {
	@Bean
	@ConfigurationProperties(prefix = "bank-flow.immudb")
	ImmuDbProperties immuDbProperties() {
		return new ImmuDbProperties();
	}

	@Bean
	@ConditionalOnProperty(prefix = "bank-flow.immudb", name = "enabled", havingValue = "true", matchIfMissing = true)
	ImmuDbClientFactory immuDbClientFactory(ImmuDbProperties properties) {
		return new ImmuDbClientFactory(properties);
	}

	@Bean
	@ConditionalOnMissingBean(LedgerAccountRepository.class)
	LedgerAccountRepository noopLedgerAccountRepository() {
		return new LedgerAccountRepository() {
			@Override
			public boolean saveIfNotExists(br.com.bankflow.ledger.domain.LedgerAccount account) {
				return true;
			}

			@Override
			public java.util.OptionalLong findAccountIdByDigitalAccountId(java.util.UUID digitalAccountId) {
				return java.util.OptionalLong.empty();
			}
		};
	}

	@Bean
	@ConditionalOnMissingBean(LedgerPostingRepository.class)
	LedgerPostingRepository noopLedgerPostingRepository() {
		return new LedgerPostingRepository() {
			@Override
			public boolean saveIfNotExists(br.com.bankflow.ledger.domain.LedgerPosting posting) {
				return true;
			}

			@Override
			public java.util.Optional<br.com.bankflow.ledger.domain.LedgerPosting> findByExternalId(String externalId) {
				return java.util.Optional.empty();
			}

			@Override
			public boolean reversalExistsFor(long entryId) {
				return false;
			}
		};
	}

	@Bean
	@ConditionalOnMissingBean(LedgerPostingPublisher.class)
	LedgerPostingPublisher noopLedgerPostingPublisher() {
		return posting -> {
		};
	}

	public static class ImmuDbProperties {
		private boolean enabled = true;
		private String host = "localhost";
		private int port = 3322;
		private String database = "defaultdb";
		private String username = "immudb";
		private String password = "immudb";

		public boolean enabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String host() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public int port() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public String database() {
			return database;
		}

		public void setDatabase(String database) {
			this.database = database;
		}

		public String username() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String password() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}
	}
}

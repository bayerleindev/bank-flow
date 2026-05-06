package br.com.bankflow.ledger.configs;

import io.codenotary.immudb4j.ImmuClient;

public class ImmuDbClientFactory {
	private final ImmuDbConfig.ImmuDbProperties properties;

	public ImmuDbClientFactory(ImmuDbConfig.ImmuDbProperties properties) {
		this.properties = properties;
	}

	public ImmuClient openClient() {
		ImmuClient client = ImmuClient.newBuilder()
				.withServerUrl(properties.host())
				.withServerPort(properties.port())
				.build();
		client.openSession(properties.database(), properties.username(), properties.password());
		return client;
	}

	public void closeClient(ImmuClient client) {
		if (client == null) {
			return;
		}
		try {
			client.closeSession();
		} catch (RuntimeException ignored) {
		}
		try {
			client.shutdown();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}

package br.com.bankflow.accounts.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientConfig {
	@Bean
	RestClient.Builder restClientBuilder(
			RestClientBuilderConfigurer configurer,
			@Value("${bank-flow.http.client.connect-timeout-ms:500}") long connectTimeoutMs,
			@Value("${bank-flow.http.client.read-timeout-ms:1500}") long readTimeoutMs
	) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(connectTimeoutMs))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
		return configurer.configure(RestClient.builder())
				.requestFactory(requestFactory);
	}
}

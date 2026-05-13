package br.com.bankflow.yielding.configs;

import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {
	@Bean
	RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
		return configurer.configure(RestClient.builder());
	}
}

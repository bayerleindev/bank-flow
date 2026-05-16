package br.com.bankflow.transfer.configs;

import br.com.bankflow.transfer.observability.TransferTracing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

import static br.com.bankflow.transfer.observability.TraceConstants.TRANSFER_ID_HEADER;

@Configuration
public class HttpClientConfig {

    @Bean(destroyMethod = "close")
    HttpClient httpClient(
            @Value("${bank-flow.http.client.connect-timeout-ms:500}") long connectTimeoutMs
    ) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Bean
    JdkClientHttpRequestFactory jdkClientHttpRequestFactory(
            HttpClient httpClient,
            @Value("${bank-flow.http.client.read-timeout-ms:1500}") long readTimeoutMs
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return requestFactory;
    }

	@Bean
	RestClient.Builder restClientBuilder(
            RestClientBuilderConfigurer configurer,
            JdkClientHttpRequestFactory requestFactory,
            TransferTracing transferTracing
	) {
		return configurer
                .configure(RestClient.builder())
				.requestFactory(requestFactory)
                .requestInterceptor(((request, body, execution) -> {
                    String transferId = transferTracing.currentTransferId();

                    if (transferId != null && !transferId.isBlank()) {
                        request.getHeaders().set(TRANSFER_ID_HEADER, transferId);
                    }

                    return execution.execute(request, body);
                }));
	}
}

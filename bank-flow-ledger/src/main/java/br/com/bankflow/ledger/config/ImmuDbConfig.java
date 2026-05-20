package br.com.bankflow.ledger.config;

import io.codenotary.immudb4j.ImmuClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ImmuDbProperties.class)
public class ImmuDbConfig {

    @Bean(destroyMethod = "shutdown")
    ImmuClient immuClient(ImmuDbProperties properties) {
        ImmuClient client =
                ImmuClient.newBuilder()
                        .withServerUrl(properties.host())
                        .withServerPort(properties.port())
                        .build();
        client.openSession(properties.database(), properties.username(), properties.password());
        return client;
    }
}

package br.com.bankflow.onboarding.api.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class RustFsConfig {

    @Bean
    S3Presigner s3Presigner(
            @Value("${app.rustfs.presigned-endpoint}") URI presignedEndpoint,
            @Value("${app.rustfs.region}") String region,
            @Value("${app.rustfs.access-key}") String accessKey,
            @Value("${app.rustfs.secret-key}") String secretKey) {
        return S3Presigner.builder()
                .endpointOverride(presignedEndpoint)
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}

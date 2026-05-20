package br.com.bankflow.transfers.api.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class KafkaObservationConfig {

    @Bean
    BeanPostProcessor kafkaTemplateObservationPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof KafkaTemplate<?, ?> kafkaTemplate) {
                    kafkaTemplate.setObservationEnabled(true);
                }
                return bean;
            }
        };
    }
}

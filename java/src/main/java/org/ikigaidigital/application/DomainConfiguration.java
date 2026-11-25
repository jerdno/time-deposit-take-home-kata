package org.ikigaidigital.application;

import org.ikigaidigital.TimeDepositCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the domain into Spring. The domain packages stay free of Spring, so their beans are declared here. The
 * calculator's no-arg constructor knows every Plan Type that exists today.
 */
@Configuration(proxyBeanMethods = false)
class DomainConfiguration {

    @Bean
    TimeDepositCalculator timeDepositCalculator() {
        return new TimeDepositCalculator();
    }
}

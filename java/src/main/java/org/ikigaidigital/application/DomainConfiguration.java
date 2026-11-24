package org.ikigaidigital.application;

import java.util.List;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.plantype.BasicPlan;
import org.ikigaidigital.plantype.PlanTypeResolver;
import org.ikigaidigital.plantype.PremiumPlan;
import org.ikigaidigital.plantype.StudentPlan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the domain into Spring. The domain packages stay free of Spring, so their beans are declared here. A new Plan
 * Type is one more entry in the list the {@link PlanTypeResolver} is built from.
 */
@Configuration(proxyBeanMethods = false)
class DomainConfiguration {

    @Bean
    PlanTypeResolver planTypeResolver() {
        return new PlanTypeResolver(List.of(new BasicPlan(), new StudentPlan(), new PremiumPlan()));
    }

    @Bean
    TimeDepositCalculator timeDepositCalculator(PlanTypeResolver planTypeResolver) {
        return new TimeDepositCalculator(planTypeResolver);
    }
}

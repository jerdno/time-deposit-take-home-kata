package org.ikigaidigital.application;

import java.util.List;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.plantype.BasicPlan;
import org.ikigaidigital.plantype.PlanType;
import org.ikigaidigital.plantype.PlanTypeResolver;
import org.ikigaidigital.plantype.PremiumPlan;
import org.ikigaidigital.plantype.StudentPlan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the domain into Spring. The domain packages stay free of Spring, so their beans are declared here. A new Plan
 * Type is one more bean: the resolver picks up every {@link PlanType} bean.
 */
@Configuration(proxyBeanMethods = false)
class DomainConfiguration {

    @Bean
    BasicPlan basicPlan() {
        return new BasicPlan();
    }

    @Bean
    StudentPlan studentPlan() {
        return new StudentPlan();
    }

    @Bean
    PremiumPlan premiumPlan() {
        return new PremiumPlan();
    }

    @Bean
    PlanTypeResolver planTypeResolver(List<PlanType> planTypes) {
        return new PlanTypeResolver(planTypes);
    }

    @Bean
    TimeDepositCalculator timeDepositCalculator(PlanTypeResolver planTypeResolver) {
        return new TimeDepositCalculator(planTypeResolver);
    }
}

package archfixture.compliant.application;

import archfixture.compliant.TimeDeposit;
import archfixture.compliant.plantype.BasicPlan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application-layer bean wiring: depends on Spring and on the domain. */
@Configuration
public class DomainConfiguration {

    @Bean
    BasicPlan basicPlan() {
        return new BasicPlan();
    }

    @Bean
    TimeDeposit timeDeposit() {
        return new TimeDeposit();
    }
}

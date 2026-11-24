package archfixture.compliant.application;

import archfixture.compliant.Deposit;
import archfixture.compliant.plantype.Plan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application-layer bean wiring: depends on Spring and on the domain. */
@Configuration
public class DomainConfiguration {

    @Bean
    Plan plan() {
        return new Plan();
    }

    @Bean
    Deposit deposit() {
        return new Deposit();
    }
}

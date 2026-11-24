package org.ikigaidigital.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves each rule in {@link HexagonalArchitecture} can fail, and that it doesn't reach beyond its own layer, by
 * evaluating it against the fixtures in {@code archfixture}.
 */
class HexagonalArchitectureRulesTest {

    private static final String VIOLATING = "archfixture.violating";
    private static final String COMPLIANT = "archfixture.compliant";

    private static final JavaClasses VIOLATING_CLASSES = new ClassFileImporter().importPackages(VIOLATING);
    private static final JavaClasses COMPLIANT_CLASSES = new ClassFileImporter().importPackages(COMPLIANT);

    @Test
    void domainRuleReportsATopLevelDomainClassThatDependsOnSpring() {
        assertThat(violations(HexagonalArchitecture.domainDependsOnlyOnTheJdkSlf4jApiAndItself(VIOLATING)))
                .anySatisfy(violation -> assertThat(violation)
                        .contains("<archfixture.violating.TimeDeposit>")
                        .contains("<org.springframework.stereotype.Component>"));
    }

    @Test
    void domainRuleReportsAPlanTypeThatDependsOnJakartaPersistenceOrWebTypes() {
        assertThat(violations(HexagonalArchitecture.domainDependsOnlyOnTheJdkSlf4jApiAndItself(VIOLATING)))
                .anySatisfy(violation -> assertThat(violation)
                        .contains("<archfixture.violating.plantype.BasicPlan>")
                        .contains("<jakarta.persistence.Entity>"))
                .anySatisfy(violation -> assertThat(violation)
                        .contains("<archfixture.violating.plantype.BasicPlan.isEligible(")
                        .contains("<jakarta.servlet.http.HttpServletRequest>"));
    }

    @Test
    void domainRuleReportsADomainClassThatDependsOnAnAdapter() {
        assertThat(violations(HexagonalArchitecture.domainDependsOnlyOnTheJdkSlf4jApiAndItself(VIOLATING)))
                .anySatisfy(violation -> assertThat(violation)
                        .contains("<archfixture.violating.TimeDepositCalculator.count(")
                        .contains("<archfixture.violating.adapter.persistence.Repository>"));
    }

    @Test
    void applicationRuleReportsAnApplicationClassThatDependsOnAnAdapter() {
        assertThat(violations(HexagonalArchitecture.applicationDoesNotDependOnAdapters(VIOLATING)))
                .anySatisfy(violation -> assertThat(violation)
                        .contains("<archfixture.violating.application.Service.controller()>")
                        .contains("<archfixture.violating.adapter.web.Controller>"));
    }

    @Test
    void adapterRuleReportsTheWebAdapterDependingOnPersistenceAndTheOtherWayRound() {
        assertThat(violations(HexagonalArchitecture.adaptersDoNotDependOnEachOther(VIOLATING)))
                .anySatisfy(violation -> assertThat(violation)
                        .contains("<archfixture.violating.adapter.web.Controller.repository()>")
                        .contains("<archfixture.violating.adapter.persistence.Repository>"))
                .anySatisfy(violation -> assertThat(violation)
                        .contains("<archfixture.violating.adapter.persistence.Repository.controller()>")
                        .contains("<archfixture.violating.adapter.web.Controller>"));
    }

    // The domain rule must not reach the root's sub-packages (application, adapters) or the composition root, all of
    // which use Spring. The application layer may use Spring and the domain.
    @Test
    void rulesAcceptALayoutThatKeepsTheBoundaries() {
        assertThat(violations(
                        HexagonalArchitecture.domainDependsOnlyOnTheJdkSlf4jApiAndItself(COMPLIANT), COMPLIANT_CLASSES))
                .isEmpty();
        assertThat(violations(HexagonalArchitecture.applicationDoesNotDependOnAdapters(COMPLIANT), COMPLIANT_CLASSES))
                .isEmpty();
        assertThat(violations(HexagonalArchitecture.adaptersDoNotDependOnEachOther(COMPLIANT), COMPLIANT_CLASSES))
                .isEmpty();
    }

    private static List<String> violations(ArchRule rule) {
        return violations(rule, VIOLATING_CLASSES);
    }

    private static List<String> violations(ArchRule rule, JavaClasses classes) {
        return rule.evaluate(classes).getFailureReport().getDetails();
    }
}

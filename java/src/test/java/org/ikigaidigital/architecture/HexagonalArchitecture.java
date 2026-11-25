package org.ikigaidigital.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;

/**
 * The hexagonal boundaries as ArchUnit rules, built for a root package ({@code org.ikigaidigital}).
 *
 * <pre>
 * root                       domain: TimeDeposit, TimeDepositCalculator (classes in root itself, not its sub-packages)
 * root.plantype..            domain: Plan Types and PlanTypeResolver
 * root.application..         use cases, ports (in/out), the model they exchange (application.model), bean wiring
 * root.adapter.web..         generated API, controller, Problem Details handler (web.exception), web infrastructure
 * root.adapter.persistence.. out-port implementations, JPA entities (persistence.entity), Spring Data repositories
 *                            (persistence.repository)
 * </pre>
 */
final class HexagonalArchitecture {

    // The @SpringBootApplication class, left out of the domain rule (see the README assumptions).
    static final String COMPOSITION_ROOT = "TimeDepositApplication";

    private HexagonalArchitecture() {}

    /**
     * The domain depends only on the JDK, the SLF4J API and itself: no Spring, no Jakarta Persistence, no web types, no
     * application or adapter packages.
     */
    static ArchRule domainDependsOnlyOnTheJdkSlf4jApiAndItself(String root) {
        String plantype = root + ".plantype..";
        String compositionRoot = root + "." + COMPOSITION_ROOT;
        return classes()
                .that()
                // A package without ".." matches that package only, so root's sub-packages are not part of this set.
                .resideInAnyPackage(root, plantype)
                .and()
                .doNotHaveFullyQualifiedName(compositionRoot)
                .should()
                .onlyDependOnClassesThat(resideInAnyPackage(root, plantype, "java..", "org.slf4j..")
                        .and(not(name(compositionRoot))))
                .as("the domain depends only on the JDK, the SLF4J API and itself");
    }

    /** The application layer may use Spring and the domain, but no adapter. */
    static ArchRule applicationDoesNotDependOnAdapters(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + ".application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(root + ".adapter..")
                .as("the application layer does not depend on adapters");
    }

    /**
     * Each adapter ({@code web}, {@code persistence}, and any added later) stands alone: none depends on another, in
     * either direction. An adapter's sub-packages (such as {@code persistence.entity}) belong to that adapter.
     */
    static ArchRule adaptersDoNotDependOnEachOther(String root) {
        return slices().matching(root + ".adapter.(*)..")
                .should()
                .notDependOnEachOther()
                .as("adapters do not depend on each other");
    }
}

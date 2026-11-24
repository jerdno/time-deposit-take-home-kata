package org.ikigaidigital.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;

/**
 * The hexagonal boundaries as ArchUnit rules, built for a root package so that they can run against the real code
 * ({@code org.ikigaidigital}) and against test fixtures laid out the same way.
 *
 * <pre>
 * root                       domain: TimeDeposit, TimeDepositCalculator (classes in root itself, not its sub-packages)
 * root.plantype..            domain: Plan Types and PlanTypeResolver
 * root.application..         use cases, ports (in/out), read model, bean wiring
 * root.adapter.web..         generated API, controller, web infrastructure
 * root.adapter.persistence.. JPA entities, repositories, out-port implementations
 * </pre>
 */
final class HexagonalArchitecture {

    // Assumption (ticket 8): the @SpringBootApplication class is the composition root (see TimeDepositApplication). It
    // is the one class in the domain package left out of the domain rule, and the domain may not depend on it.
    static final String COMPOSITION_ROOT = "TimeDepositApplication";

    private HexagonalArchitecture() {}

    /**
     * The domain depends only on the JDK, the SLF4J API and itself: no Spring, no Jakarta Persistence, no web types, no
     * application or adapter packages.
     */
    // Assumption (ticket 8): the rule is an allow-list, stricter than the ticket's "no Spring, JPA, web types or
    // adapters". It also keeps the domain off the application layer and off any other library; a new library in the
    // domain is then a deliberate change to this rule.
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
     * either direction.
     */
    static ArchRule adaptersDoNotDependOnEachOther(String root) {
        return slices().matching(root + ".adapter.(*)..")
                .should()
                .notDependOnEachOther()
                .as("adapters do not depend on each other");
    }
}

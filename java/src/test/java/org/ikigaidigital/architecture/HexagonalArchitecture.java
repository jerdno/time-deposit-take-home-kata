package org.ikigaidigital.architecture;

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

    // Assumption (ticket 8): the @SpringBootApplication class sits in the root package, which is also the domain
    // package. Moving it would change Spring's component scanning, so it stays and is treated as the composition root:
    // exactly this one class is left out of the domain rules.
    static final String COMPOSITION_ROOT = "TimeDepositApplication";

    private HexagonalArchitecture() {}

    /**
     * The domain depends only on the JDK, the SLF4J API and itself: no Spring, no Jakarta Persistence, no web types, no
     * application or adapter packages.
     */
    static ArchRule domainDependsOnlyOnJavaSlf4jAndItself(String root) {
        String plantype = root + ".plantype..";
        return classes()
                .that()
                // A package without ".." matches that package only, so root's sub-packages are not part of this set.
                .resideInAnyPackage(root, plantype)
                .and()
                .doNotHaveFullyQualifiedName(root + "." + COMPOSITION_ROOT)
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(root, plantype, "java..", "org.slf4j..")
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

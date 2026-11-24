package org.ikigaidigital.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Fails the build when production code crosses a hexagonal boundary. */
class HexagonalArchitectureTest {

    private static final String ROOT = "org.ikigaidigital";

    // Production classes only, including the code generated from openapi.yaml; test classes are left out.
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ROOT);

    @Test
    void domainDependsOnlyOnTheJdkTheSlf4jApiAndItself() {
        HexagonalArchitecture.domainDependsOnlyOnJavaSlf4jAndItself(ROOT).check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationLayerDoesNotDependOnAdapters() {
        HexagonalArchitecture.applicationDoesNotDependOnAdapters(ROOT).check(PRODUCTION_CLASSES);
    }

    @Test
    void adaptersDoNotDependOnEachOther() {
        HexagonalArchitecture.adaptersDoNotDependOnEachOther(ROOT).check(PRODUCTION_CLASSES);
    }
}

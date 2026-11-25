package org.ikigaidigital.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.ikigaidigital.adapter.persistence.entity.ExpectedVersion;
import org.ikigaidigital.adapter.persistence.entity.VersionedEntity;
import org.junit.jupiter.api.Test;

/**
 * Optimistic locking cannot be skipped: every method that changes a {@link VersionedEntity} takes the {@link
 * ExpectedVersion} the change was computed from and checks it first
 * (docs/adr/0001-optimistic-locking-for-the-balance-update.md).
 */
class VersionedEntitiesTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("org.ikigaidigital");

    @Test
    void everyMethodThatChangesAVersionedEntityChecksTheExpectedVersion() {
        methods()
                .that()
                .areDeclaredInClassesThat()
                .areAssignableTo(VersionedEntity.class)
                .and()
                .areNotDeclaredIn(VersionedEntity.class)
                .and()
                .areNotPrivate()
                .and()
                .areNotStatic()
                .and(changeTheEntity())
                .should(takeAnExpectedVersion())
                .andShould(callRequireVersion())
                .because("a change that skips the version check silently overwrites a concurrent one")
                .check(PRODUCTION_CLASSES);
    }

    // Getters, JPA lifecycle callbacks (such as @PreUpdate) and compiler-generated methods are not changes the
    // application makes.
    private static DescribedPredicate<JavaMethod> changeTheEntity() {
        return DescribedPredicate.describe(
                "change the entity",
                method -> !method.getName().startsWith("get")
                        && !method.getName().startsWith("is")
                        && method.getAnnotations().stream()
                                .noneMatch(annotation ->
                                        annotation.getRawType().getPackageName().equals("jakarta.persistence"))
                        && !method.getModifiers().contains(JavaModifier.SYNTHETIC)
                        && !method.getModifiers().contains(JavaModifier.BRIDGE));
    }

    private static ArchCondition<JavaMethod> callRequireVersion() {
        return new ArchCondition<>("call requireVersion") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean callsIt = method.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTarget().getName().equals("requireVersion")
                                && call.getTarget().getOwner().isAssignableTo(VersionedEntity.class));
                events.add(new SimpleConditionEvent(
                        method,
                        callsIt,
                        method.getFullName() + (callsIt ? " calls" : " does not call") + " requireVersion"));
            }
        };
    }

    private static ArchCondition<JavaMethod> takeAnExpectedVersion() {
        return new ArchCondition<>("take an ExpectedVersion") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean takesIt = method.getRawParameterTypes().stream()
                        .anyMatch(type -> type.isEquivalentTo(ExpectedVersion.class));
                events.add(new SimpleConditionEvent(
                        method,
                        takesIt,
                        method.getFullName() + (takesIt ? " takes" : " does not take") + " an ExpectedVersion"));
            }
        };
    }
}

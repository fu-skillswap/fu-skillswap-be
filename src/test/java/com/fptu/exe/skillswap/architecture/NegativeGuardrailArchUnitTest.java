package com.fptu.exe.skillswap.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NegativeGuardrailArchUnitTest {

    @Test
    void intentionalCrossModuleInternalDependencyMustFailRule() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importPackages(
                        "com.fptu.exe.skillswap.modules.booking.support",
                        "com.fptu.exe.skillswap.modules.mentor.service"
                );

        EvaluationResult result = ArchitectureGuardrailTest.modules_must_not_depend_on_other_module_internals
                .evaluate(violatingClasses);

        assertThat(result.hasViolation())
                .as("ArchUnit guardrail must fail when a class depends on another module's internal service")
                .isTrue();

        assertThat(result.getFailureReport().toString())
                .contains("depends on internal type");
    }

    @Test
    void compliantPublicContractDependencyMustPassRule() {
        JavaClasses compliantClasses = new ClassFileImporter()
                .importPackages(
                        "com.fptu.exe.skillswap.modules.booking.support",
                        "com.fptu.exe.skillswap.modules.mentor.port"
                );

        EvaluationResult result = ArchitectureGuardrailTest.modules_must_not_depend_on_other_module_internals
                .evaluate(compliantClasses);

        assertThat(result.hasViolation())
                .as("ArchUnit guardrail must pass when a class depends only on another module's public port")
                .isFalse();
    }
}

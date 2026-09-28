package com.mediflow.surgery;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Enforces the Surgery dependency direction in docs/ai/04-microservice-blueprint.md. */
@AnalyzeClasses(packages = "com.mediflow.surgery", importOptions = ImportOption.DoNotIncludeTests.class)
class SurgeryArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_pure = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.mediflow.surgery.application..",
                    "com.mediflow.surgery.infrastructure..",
                    "org.springframework..", "jakarta.persistence..", "jakarta.validation..",
                    "java.sql..", "java.net..", "java.io..");

    @ArchTest
    static final ArchRule application_does_not_reach_adapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.mediflow.surgery.infrastructure..",
                    "org.springframework.data..", "org.springframework.web..",
                    "org.springframework.amqp..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule web_does_not_bypass_application = noClasses()
            .that().resideInAPackage("..infrastructure.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure.persistence..", "..infrastructure.client..",
                    "..infrastructure.messaging..", "com.mediflow.surgery.domain..");

    @ArchTest
    static final ArchRule no_layer_cycles = SlicesRuleDefinition.slices()
            .matching("com.mediflow.surgery.(*)..")
            .should().beFreeOfCycles();
}

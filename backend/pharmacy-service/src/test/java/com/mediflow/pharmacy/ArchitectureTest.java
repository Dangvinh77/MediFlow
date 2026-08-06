package com.mediflow.pharmacy;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the clean-architecture dependency rule (docs/ai/04-microservice-blueprint.md):
 * application → domain, inward only. Driving adapters (web/, messaging/consumer/) call application;
 * driven adapters (infrastructure/) implement application's out-ports.
 */
@AnalyzeClasses(packages = "com.mediflow.pharmacy", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    /** domain/ imports nothing from Spring or Jakarta Persistence/Validation — pure Java. */
    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.validation..")
            .allowEmptyShould(true)
            .as("domain must be plain Java (no Spring, no Jakarta Persistence/Validation)");

    /** domain/ never depends on application, infrastructure, web or messaging. */
    @ArchTest
    static final ArchRule domain_does_not_depend_outward = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.mediflow.pharmacy.application..",
                    "com.mediflow.pharmacy.infrastructure..",
                    "com.mediflow.pharmacy.web..",
                    "com.mediflow.pharmacy.messaging..")
            .allowEmptyShould(true)
            .as("domain must not depend on application/infrastructure/web/messaging");

    /** application/ imports no infrastructure types (JPA, AMQP, Spring Data, HTTP). */
    @ArchTest
    static final ArchRule application_does_not_import_infrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.data..",
                    "jakarta.persistence..",
                    "org.springframework.amqp..",
                    "org.springframework.web..",
                    "com.mediflow.pharmacy.infrastructure..",
                    "com.mediflow.pharmacy.web..",
                    "com.mediflow.pharmacy.messaging..")
            .allowEmptyShould(true)
            .as("application must not depend on Spring Data, JPA, AMQP, HTTP, or infrastructure/web/messaging");

    /** web/ (driving HTTP) depends only on application — never on persistence/messaging/infrastructure/domain. */
    @ArchTest
    static final ArchRule web_depends_only_on_application = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.mediflow.pharmacy.infrastructure..",
                    "com.mediflow.pharmacy.messaging..",
                    "com.mediflow.pharmacy.domain..")
            .allowEmptyShould(true)
            .as("web controllers must depend only on application (in-ports + DTOs), not on infrastructure/messaging/domain");

    /** No package cycles among the layers (domain, application, infrastructure, web, messaging). */
    @ArchTest
    static final ArchRule no_cycles = SlicesRuleDefinition.slices()
            .matching("com.mediflow.pharmacy.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);
}

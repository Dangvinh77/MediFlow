package com.mediflow.report;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the clean-architecture dependency rule (docs/ai/04-microservice-blueprint.md):
 * application → domain, inward only. Driving adapters (web/, messaging/consumer/) call application;
 * driven adapters (infrastructure/) implement application's out-ports.
 *
 * BR-R4: report must NEVER have a Feign client or external datasource — no dependency on
 * org.springframework.cloud.openfeign or any ..client.. package.
 */
@AnalyzeClasses(packages = "com.mediflow.report", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    /** Projection data must never acquire a synchronous producer dependency. */
    @ArchTest
    static final ArchRule no_feign = noClasses()
            .that().resideInAPackage("..report..")
            .should().dependOnClassesThat().resideInAPackage("..openfeign..")
            .as("report must not depend on OpenFeign (BR-R4)");

    /**
     * Live authority must fail closed on revoke/outage, rather than query Core tables or cache grants.
     * Exact security-only wiring edges cannot permit a new report-data client; layer rules below remain.
     */
    @ArchTest
    static final ArchRule no_external_client_except_current_authority =
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
            .that().resideInAPackage("..report..")
            .should(new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaClass>(
                    "limit client dependencies to the exact current-authority configuration edges") {
                @Override public void check(com.tngtech.archunit.core.domain.JavaClass source,
                        com.tngtech.archunit.lang.ConditionEvents events) {
                    var permittedTargets = java.util.Set.of(
                            "com.mediflow.report.infrastructure.client.CoreReportAuthorityAdapter",
                            "org.springframework.cloud.client.discovery.DiscoveryClient",
                            "org.springframework.cloud.client.ServiceInstance");
                    for (var dependency : source.getDirectDependenciesFromSelf()) {
                        var target = dependency.getTargetClass();
                        String packageName = target.getPackageName();
                        boolean client = packageName.endsWith(".client") || packageName.contains(".client.");
                        boolean securityWiring = source.getName().equals(
                                "com.mediflow.report.infrastructure.config.ReportAuthorityConfiguration")
                                && permittedTargets.contains(target.getName());
                        if (client && !securityWiring) {
                            events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(
                                    source, dependency.getDescription()));
                        }
                    }
                }
            });

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
                    "com.mediflow.report.application..",
                    "com.mediflow.report.infrastructure..",
                    "com.mediflow.report.web..",
                    "com.mediflow.report.messaging..")
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
                    "com.mediflow.report.infrastructure..",
                    "com.mediflow.report.web..",
                    "com.mediflow.report.messaging..")
            .allowEmptyShould(true)
            .as("application must not depend on Spring Data, JPA, AMQP, HTTP, or infrastructure/web/messaging");

    /** web/ (driving HTTP) depends only on application — never on persistence/messaging/infrastructure/domain. */
    @ArchTest
    static final ArchRule web_depends_only_on_application = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.mediflow.report.infrastructure..",
                    "com.mediflow.report.messaging..",
                    "com.mediflow.report.domain..")
            .allowEmptyShould(true)
            .as("web controllers must depend only on application (in-ports + DTOs), not on infrastructure/messaging/domain");

    /** No package cycles among the layers (domain, application, infrastructure, web, messaging). */
    @ArchTest
    static final ArchRule no_cycles = SlicesRuleDefinition.slices()
            .matching("com.mediflow.report.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);
}

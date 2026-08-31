package com.fptu.exe.skillswap.architecture;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.fptu.exe.skillswap",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureGuardrailTest {

    @ArchTest
    public static final ArchRule shared_must_not_depend_on_modules =
            noClasses()
                    .that().resideInAPackage("..shared..")
                    .should().dependOnClassesThat().resideInAnyPackage("..modules..")
                    .as("shared must not depend on business modules");

    @ArchTest
    public static final ArchRule infrastructure_must_not_depend_on_module_services =
            noClasses()
                    .that().resideInAPackage("..infrastructure..")
                    .should().dependOnClassesThat().resideInAnyPackage("..modules..service..")
                    .as("infrastructure must not depend on business services directly");

    @ArchTest
    public static final ArchRule controllers_must_not_access_repositories =
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().accessClassesThat().resideInAnyPackage("..repository..")
                    .as("controllers must not access repositories directly");

    public static final ArchRule repositories_must_not_be_accessed_cross_module =
            classes()
                    .that().resideInAPackage("..modules..")
                    .should(new CrossModuleRepositoryAccessCondition())
                    .as("repositories must not be accessed across module boundaries");

    public static final ArchRule modules_must_not_depend_on_other_module_internals =
            classes()
                    .that().resideInAPackage("..modules..")
                    .should(new CrossModuleInternalDependencyCondition())
                    .as("modules must use another module's public port/event/model, never its service, controller, repository, or entity");

    public static final ArchRule provider_modules_must_not_depend_on_admin_dtos =
            classes()
                    .that().resideInAPackage("..modules..")
                    .should(new CrossModuleAdminDtoDependencyCondition())
                    .as("business modules must not depend on admin web DTOs");

    public static final ArchRule business_modules_must_not_use_object_provider =
            classes()
                    .that().resideInAPackage("..modules..")
                    .should(new CrossModuleObjectProviderInternalDependencyCondition())
                    .as("business modules must not use ObjectProvider to defer or hide a cross-module internal dependency");

    @ArchTest
    public static final ArchRule api_contracts_must_not_depend_on_jpa_or_spring_implementation_types =
            classes()
                    .that().resideInAnyPackage("..modules..port..")
                    .should(new PublicApiDependencyCondition())
                    .as("module APIs must contain only IDs, JDK types, shared value types, and immutable projections");

    public static final ArchRule module_entities_should_follow_table_naming_convention =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .should(new EntityTableNamingCondition())
                    .as("entities should follow module table naming convention");

    @ArchTest
    public static final ArchRule mentor_profile_service_must_not_depend_on_booking_service =
            noClasses()
                    .that().haveFullyQualifiedName("com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.fptu.exe.skillswap.modules.booking.service.BookingService");

    @ArchTest
    public static final ArchRule admin_mentor_verification_moderation_service_must_not_depend_on_notification_service =
            noClasses()
                    .that().haveFullyQualifiedName("com.fptu.exe.skillswap.modules.admin.service.AdminMentorVerificationModerationService")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.fptu.exe.skillswap.modules.notification.service.NotificationService");

    @ArchTest
    public static final ArchRule course_services_must_not_depend_on_bunny_sdk_contracts =
            noClasses()
                    .that().resideInAPackage("..modules.course.service..")
                    .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.bunny..");

    @ArchTest
    public static final ArchRule payment_services_must_not_depend_on_payos_contracts =
            noClasses()
                    .that().resideInAPackage("..modules.payment.service..")
                    .should().dependOnClassesThat().resideInAnyPackage("..modules.payment.integration.payos..");

    @ArchTest
    public static final ArchRule chat_attachment_service_must_use_narrow_storage_capabilities =
            noClasses()
                    .that().haveFullyQualifiedName("com.fptu.exe.skillswap.modules.chat.service.ChatAttachmentService")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.fptu.exe.skillswap.infrastructure.storage.StorageGateway");

    public static final class CrossModuleRepositoryAccessCondition extends ArchCondition<JavaClass> {
        public CrossModuleRepositoryAccessCondition() {
            super("avoid accessing repositories of other modules");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            String originModule = moduleNameOf(javaClass.getPackageName());
            if (originModule == null) {
                return;
            }
            for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
                JavaClass target = ownerOf(access);
                if (target == null || !target.getPackageName().contains(".repository.")) {
                    continue;
                }
                String targetModule = moduleNameOf(target.getPackageName());
                if (targetModule != null && !targetModule.equals(originModule)) {
                    events.add(SimpleConditionEvent.violated(
                            access,
                            javaClass.getName() + " accesses repository " + target.getName() + " across module boundary"
                    ));
                }
            }
        }

        private JavaClass ownerOf(JavaAccess<?> access) {
            if (access.getTarget() instanceof HasOwner<?> targetWithOwner) {
                Object owner = targetWithOwner.getOwner();
                if (owner instanceof JavaClass javaClass) {
                    return javaClass;
                }
            }
            return access.getTargetOwner();
        }
    }

    public static final class CrossModuleInternalDependencyCondition extends ArchCondition<JavaClass> {
        public CrossModuleInternalDependencyCondition() {
            super("avoid depending on internal types of another module");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            String originModule = moduleNameOf(javaClass.getPackageName());
            if (originModule == null) {
                return;
            }

            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetModule = moduleNameOf(target.getPackageName());
                if (targetModule == null || targetModule.equals(originModule) || !isInternal(target)) {
                    continue;
                }
                events.add(SimpleConditionEvent.violated(
                        dependency,
                        javaClass.getName() + " depends on internal type " + target.getName()
                                + " of module " + targetModule
                ));
            }
        }

        private boolean isInternal(JavaClass target) {
            String packageName = target.getPackageName();
            return packageName.contains(".repository.")
                    || packageName.contains(".service.")
                    || packageName.contains(".controller.")
                    || target.isAnnotatedWith(Entity.class);
        }
    }

    public static final class CrossModuleAdminDtoDependencyCondition extends ArchCondition<JavaClass> {
        public CrossModuleAdminDtoDependencyCondition() {
            super("avoid depending on admin DTOs from another module");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            String originModule = moduleNameOf(javaClass.getPackageName());
            if (originModule == null || "admin".equals(originModule)) {
                return;
            }
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (target.getPackageName().contains(".modules.admin.dto.")) {
                    events.add(SimpleConditionEvent.violated(
                            dependency,
                            javaClass.getName() + " depends on admin DTO " + target.getName()
                    ));
                }
            }
        }
    }

    /**
     * ObjectProvider is a valid Spring mechanism for optional technical adapters. The architectural
     * boundary is crossed only when its generic dependency is an internal type of another module;
     * those generic dependencies are present in ArchUnit's direct dependency graph.
     */
    public static final class CrossModuleObjectProviderInternalDependencyCondition extends ArchCondition<JavaClass> {
        public CrossModuleObjectProviderInternalDependencyCondition() {
            super("avoid ObjectProvider for another module's internals");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            String originModule = moduleNameOf(javaClass.getPackageName());
            if (originModule == null) {
                return;
            }
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetModule = moduleNameOf(target.getPackageName());
                if (targetModule != null && !targetModule.equals(originModule)
                        && (target.getPackageName().contains(".repository.")
                        || target.getPackageName().contains(".service.")
                        || target.getPackageName().contains(".controller.")
                        || target.isAnnotatedWith(Entity.class))) {
                    String description = dependency.getDescription();
                    if (description.contains("ObjectProvider")) {
                        events.add(SimpleConditionEvent.violated(dependency,
                                javaClass.getName() + " defers internal dependency " + target.getName() + " through ObjectProvider"));
                    }
                }
            }
        }
    }

    /**
     * Public port packages are the only named API surface.  Keep package-info's
     * NamedInterface annotation out of the dependency check, then reject
     * framework/JPA types and implementation DTOs from another business module.
     * Same-owner migration is intentionally handled by that owner's task; this
     * condition prevents a new cross-module leak from being introduced now.
     */
    public static final class PublicApiDependencyCondition extends ArchCondition<JavaClass> {
        public PublicApiDependencyCondition() {
            super("avoid framework and foreign implementation types in public ports");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            if (javaClass.getName().endsWith(".package-info")) {
                return;
            }
            String originModule = moduleNameOf(javaClass.getPackageName());
            if (originModule == null) {
                return;
            }
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetPackage = target.getPackageName();
                String targetModule = moduleNameOf(targetPackage);
                boolean frameworkType = targetPackage.startsWith("jakarta.persistence.")
                        || targetPackage.startsWith("org.springframework.");
                boolean foreignImplementation = targetModule != null
                        && !originModule.equals(targetModule)
                        && (targetPackage.contains(".domain.")
                        || targetPackage.contains(".dto.")
                        || targetPackage.contains(".repository.")
                        || targetPackage.contains(".service.")
                        || targetPackage.contains(".controller.")
                        || target.isAnnotatedWith(Entity.class));
                if (frameworkType || foreignImplementation) {
                    events.add(SimpleConditionEvent.violated(
                            dependency,
                            javaClass.getName() + " exposes forbidden API dependency " + target.getName()));
                }
            }
        }
    }

    private static final class EntityTableNamingCondition extends ArchCondition<JavaClass> {
        private EntityTableNamingCondition() {
            super("map to tables with module ownership naming");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            String module = moduleNameOf(javaClass.getPackageName());
            if (module == null) {
                return;
            }
            Table table = javaClass.reflect().getAnnotation(Table.class);
            String tableName = table != null ? table.name() : "";
            boolean valid = tableName.startsWith(module + "_");
            if (!valid) {
                events.add(SimpleConditionEvent.violated(
                        javaClass,
                        javaClass.getName() + " maps to table '" + tableName + "' which does not start with module prefix '" + module + "_'"
                ));
            }
        }
    }

    public static String moduleNameOf(String packageName) {
        String marker = ".modules.";
        int markerIndex = packageName.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        String remaining = packageName.substring(markerIndex + marker.length());
        int dotIndex = remaining.indexOf('.');
        return dotIndex >= 0 ? remaining.substring(0, dotIndex) : remaining;
    }
}

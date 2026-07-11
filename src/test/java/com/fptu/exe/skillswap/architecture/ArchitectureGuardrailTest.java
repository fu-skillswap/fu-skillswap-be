package com.fptu.exe.skillswap.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.fptu.exe.skillswap",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureGuardrailTest {

    @ArchTest
    static final ArchRule shared_must_not_depend_on_modules =
            freeze(noClasses()
                    .that().resideInAPackage("..shared..")
                    .should().dependOnClassesThat().resideInAnyPackage("..modules.."))
                    .as("shared must not depend on business modules");

    @ArchTest
    static final ArchRule infrastructure_must_not_depend_on_module_services =
            freeze(noClasses()
                    .that().resideInAPackage("..infrastructure..")
                    .should().dependOnClassesThat().resideInAnyPackage("..modules..service.."))
                    .as("infrastructure must not depend on business services directly");

    @ArchTest
    static final ArchRule controllers_must_not_access_repositories =
            freeze(noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().accessClassesThat().resideInAnyPackage("..repository.."))
                    .as("controllers must not access repositories directly");

    @ArchTest
    static final ArchRule repositories_must_not_be_accessed_cross_module =
            freeze(noClasses()
                    .that().resideInAPackage("..modules..")
                    .should(new CrossModuleRepositoryAccessCondition()))
                    .as("repositories must not be accessed across module boundaries");

    @ArchTest
    static final ArchRule module_entities_should_follow_table_naming_convention =
            freeze(noClasses()
                    .that().areAnnotatedWith(Entity.class)
                    .should(new EntityTableNamingCondition()))
                    .as("entities should follow module table naming convention");

    @ArchTest
    static final ArchRule mentor_profile_service_must_not_depend_on_booking_service =
            noClasses()
                    .that().haveFullyQualifiedName("com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.fptu.exe.skillswap.modules.booking.service.BookingService");

    @ArchTest
    static final ArchRule admin_mentor_verification_moderation_service_must_not_depend_on_notification_service =
            noClasses()
                    .that().haveFullyQualifiedName("com.fptu.exe.skillswap.modules.admin.service.AdminMentorVerificationModerationService")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.fptu.exe.skillswap.modules.notification.service.NotificationService");

    private static FreezingArchRule freeze(ArchRule rule) {
        return FreezingArchRule.freeze(rule);
    }

    private static final class CrossModuleRepositoryAccessCondition extends ArchCondition<JavaClass> {
        private CrossModuleRepositoryAccessCondition() {
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

    private static String moduleNameOf(String packageName) {
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

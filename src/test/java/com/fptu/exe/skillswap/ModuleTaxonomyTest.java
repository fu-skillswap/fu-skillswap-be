package com.fptu.exe.skillswap;

import com.fptu.exe.skillswap.modules.booking.port.BookingIssueEvidencePort;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleTaxonomyTest {

    private static final List<String> EXPECTED_BUSINESS_MODULES = List.of(
            "admin",
            "blog",
            "booking",
            "catalog",
            "chat",
            "course",
            "feedback",
            "filestorage",
            "forum",
            "identity",
            "mentor",
            "notification",
            "payment"
    );

    @Test
    void verifyModulithTaxonomyAndModuleDetection() {
        ApplicationModules modules = ApplicationModules.of(ProjectApplication.class);

        List<String> detectedModules = modules.stream()
                .map(ApplicationModule::getName)
                .sorted()
                .toList();

        assertThat(detectedModules)
                .as("All 13 baseline business modules must be detected")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_BUSINESS_MODULES);

        // Technical layers and legacy packages must NOT be declared as business application modules
        assertThat(modules.getModuleByName("demo")).isNotPresent();
        assertThat(modules.getModuleByName("seo")).isNotPresent();
        assertThat(modules.getModuleByName("system")).isNotPresent();

        var booking = modules.getModuleByName("booking");
        assertThat(booking).isPresent();
        assertThat(booking.orElseThrow().getNamedInterfaces().stream()
                .anyMatch(api -> api.contains(BookingIssueEvidencePort.class))).isTrue();

    }
}

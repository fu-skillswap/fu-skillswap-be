package com.fptu.exe.skillswap;

import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.port.BookingIssueEvidencePort;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithTest {

    @Test
    void verifyModulith() {
        ApplicationModules modules = ApplicationModules.of(ProjectApplication.class);

        var booking = modules.getModuleByName("booking");
        assertThat(booking).isPresent();
        assertThat(booking.orElseThrow().getNamedInterfaces().stream()
                .anyMatch(api -> api.contains(BookingResponse.class))).isTrue();
        assertThat(booking.orElseThrow().getNamedInterfaces().stream()
                .anyMatch(api -> api.contains(BookingIssueEvidencePort.class))).isTrue();
        assertThat(modules.getModuleByName("chat")).isPresent();
        assertThat(modules.getModuleByName("notification")).isPresent();
        assertThat(modules.getModuleByName("course")).isPresent();
    }
}

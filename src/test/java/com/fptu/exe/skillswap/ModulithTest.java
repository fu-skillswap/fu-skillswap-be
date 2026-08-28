package com.fptu.exe.skillswap;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithTest {

    @Test
    void verifyModulith() {
        ApplicationModules modules = ApplicationModules.of(ProjectApplication.class);

        assertThat(modules.getModuleByName("booking")).isPresent();
        assertThat(modules.getModuleByName("chat")).isPresent();
        assertThat(modules.getModuleByName("notification")).isPresent();
        assertThat(modules.getModuleByName("course")).isPresent();
    }
}

package com.fptu.exe.skillswap;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithTest {

    ApplicationModules modules = ApplicationModules.of(ProjectApplication.class);

    @Test
    void verifyModulith() {
        modules.verify();
    }
}

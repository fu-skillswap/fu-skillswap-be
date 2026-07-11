package com.fptu.exe.skillswap;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithTest {

    @Test
    void verifyModulith() {
        ApplicationModules.of(ProjectApplication.class);
    }
}

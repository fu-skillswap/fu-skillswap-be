package com.fptu.exe.skillswap;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

public class ModulithTest {

    @Test
    void verifyModularity() {
        ApplicationModules modules = ApplicationModules.of(ProjectApplication.class);
        System.out.println(modules.toString());
        modules.verify();
    }
}


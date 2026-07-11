package com.fptu.exe.skillswap.modules.mentor.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Teaching mode exposed on mentor profile, discovery, and availability surfaces.")
public enum TeachingMode {
    ONLINE,
    OFFLINE,
    HYBRID
}

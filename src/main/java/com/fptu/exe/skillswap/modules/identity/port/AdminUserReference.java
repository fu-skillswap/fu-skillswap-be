package com.fptu.exe.skillswap.modules.identity.port;

import java.util.UUID;

/** Identity projection used by operational modules; never exposes the User aggregate. */
public record AdminUserReference(UUID userId, String displayName) { }

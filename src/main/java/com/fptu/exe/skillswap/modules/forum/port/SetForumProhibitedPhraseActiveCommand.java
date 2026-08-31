package com.fptu.exe.skillswap.modules.forum.port;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetForumProhibitedPhraseActiveCommand(@NotNull Boolean isActive,
                                                     @NotNull @Min(0) Integer expectedVersion) { }

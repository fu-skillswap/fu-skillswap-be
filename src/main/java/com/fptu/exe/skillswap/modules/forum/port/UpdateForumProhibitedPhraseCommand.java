package com.fptu.exe.skillswap.modules.forum.port;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateForumProhibitedPhraseCommand(@NotBlank @Size(max = 200) String phrase,
                                                  @NotNull @Min(0) Integer expectedVersion) { }

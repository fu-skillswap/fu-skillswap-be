package com.fptu.exe.skillswap.modules.forum.port;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateForumProhibitedPhraseCommand(@NotBlank @Size(max = 200) String phrase) { }

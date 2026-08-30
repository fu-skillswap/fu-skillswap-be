package com.fptu.exe.skillswap.modules.mentor.port.dto;

public record MentorVerificationDecisionCommand(
        String reason,
        String adminNote
) {}

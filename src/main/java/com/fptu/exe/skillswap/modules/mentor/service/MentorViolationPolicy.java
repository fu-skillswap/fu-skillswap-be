package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationSeverity;

import java.math.BigDecimal;

public final class MentorViolationPolicy {

    private MentorViolationPolicy() {
    }

    public static BigDecimal pointsFor(MentorViolationType type) {
        return switch (type) {
            case LATE_CANCELLATION -> new BigDecimal("0.50");
            case COMPLETION_OVERDUE -> new BigDecimal("1.00");
            case MENTOR_NO_SHOW -> new BigDecimal("3.00");
            case BOOKING_POLICY_BREACH, CHAT_POLICY_BREACH, FORUM_POLICY_BREACH,
                    VERIFICATION_FRAUD, ADMIN_CONFIRMED_BREACH -> pointsForSeverity(severityFor(type));
        };
    }

    public static MentorViolationSeverity severityFor(MentorViolationType type) {
        return switch (type) {
            case LATE_CANCELLATION -> MentorViolationSeverity.LOW;
            case COMPLETION_OVERDUE -> MentorViolationSeverity.MEDIUM;
            case MENTOR_NO_SHOW -> MentorViolationSeverity.HIGH;
            case BOOKING_POLICY_BREACH, CHAT_POLICY_BREACH, FORUM_POLICY_BREACH -> MentorViolationSeverity.MEDIUM;
            case VERIFICATION_FRAUD, ADMIN_CONFIRMED_BREACH -> MentorViolationSeverity.HIGH;
        };
    }

    public static BigDecimal pointsForSeverity(MentorViolationSeverity severity) {
        return switch (severity == null ? MentorViolationSeverity.LOW : severity) {
            case LOW -> new BigDecimal("1.00");
            case MEDIUM -> new BigDecimal("3.00");
            case HIGH -> new BigDecimal("5.00");
            case CRITICAL -> new BigDecimal("10.00");
        };
    }
}

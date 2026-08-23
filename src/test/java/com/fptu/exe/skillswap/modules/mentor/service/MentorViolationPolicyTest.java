package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationSeverity;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MentorViolationPolicyTest {

    @Test
    void bookingViolations_shouldKeepAgreedPointValues() {
        assertEquals(new BigDecimal("0.50"), MentorViolationPolicy.pointsFor(MentorViolationType.LATE_CANCELLATION));
        assertEquals(new BigDecimal("1.00"), MentorViolationPolicy.pointsFor(MentorViolationType.COMPLETION_OVERDUE));
        assertEquals(new BigDecimal("3.00"), MentorViolationPolicy.pointsFor(MentorViolationType.MENTOR_NO_SHOW));
    }

    @Test
    void adminSeverity_shouldUseCentralPointScale() {
        assertEquals(new BigDecimal("1.00"), MentorViolationPolicy.pointsForSeverity(MentorViolationSeverity.LOW));
        assertEquals(new BigDecimal("3.00"), MentorViolationPolicy.pointsForSeverity(MentorViolationSeverity.MEDIUM));
        assertEquals(new BigDecimal("5.00"), MentorViolationPolicy.pointsForSeverity(MentorViolationSeverity.HIGH));
        assertEquals(new BigDecimal("10.00"), MentorViolationPolicy.pointsForSeverity(MentorViolationSeverity.CRITICAL));
    }
}

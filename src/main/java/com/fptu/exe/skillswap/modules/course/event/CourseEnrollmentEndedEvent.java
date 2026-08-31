package com.fptu.exe.skillswap.modules.course.event;

import java.util.UUID;

/** Published when a learner loses course access due to a completed refund. */
public record CourseEnrollmentEndedEvent(UUID eventId, UUID enrollmentId, UUID courseId, UUID studentUserId) {
}

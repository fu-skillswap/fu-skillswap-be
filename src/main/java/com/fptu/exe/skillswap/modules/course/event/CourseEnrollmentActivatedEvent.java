package com.fptu.exe.skillswap.modules.course.event;

import java.util.UUID;

/** Published when a learner has a persisted active enrollment. */
public record CourseEnrollmentActivatedEvent(UUID eventId, UUID enrollmentId, UUID courseId, UUID studentUserId) {
}

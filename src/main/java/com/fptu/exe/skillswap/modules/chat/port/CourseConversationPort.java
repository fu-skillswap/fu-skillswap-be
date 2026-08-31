package com.fptu.exe.skillswap.modules.chat.port;

import java.util.UUID;

/**
 * Narrow capability exposed by chat to the course module.
 *
 * <p>Course owns enrollment; chat owns conversation membership.  The port
 * intentionally exposes neither a chat entity nor a chat service.</p>
 */
public interface CourseConversationPort {

    void addCourseStudentParticipant(UUID courseId, UUID studentUserId);

    void revokeCourseStudentParticipant(UUID courseId, UUID studentUserId);
}

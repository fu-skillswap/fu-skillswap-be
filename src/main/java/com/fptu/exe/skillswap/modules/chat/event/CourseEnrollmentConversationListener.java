package com.fptu.exe.skillswap.modules.chat.event;

import com.fptu.exe.skillswap.modules.chat.port.CourseConversationPort;
import com.fptu.exe.skillswap.modules.course.event.CourseEnrollmentActivatedEvent;
import com.fptu.exe.skillswap.modules.course.event.CourseEnrollmentEndedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Chat owns membership; it consumes course lifecycle facts after the producer transaction commits. */
@Component
@RequiredArgsConstructor
class CourseEnrollmentConversationListener {

    private final CourseConversationPort courseConversationPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivated(CourseEnrollmentActivatedEvent event) {
        courseConversationPort.addCourseStudentParticipant(event.courseId(), event.studentUserId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnded(CourseEnrollmentEndedEvent event) {
        courseConversationPort.revokeCourseStudentParticipant(event.courseId(), event.studentUserId());
    }
}

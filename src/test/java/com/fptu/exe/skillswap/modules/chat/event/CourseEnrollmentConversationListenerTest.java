package com.fptu.exe.skillswap.modules.chat.event;

import com.fptu.exe.skillswap.modules.chat.port.CourseConversationPort;
import com.fptu.exe.skillswap.modules.course.event.CourseEnrollmentActivatedEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CourseEnrollmentConversationListenerTest {

    @Test
    void activationDoesNotAddNewStudentToLegacyCourseGroup() {
        CourseConversationPort courseConversationPort = mock(CourseConversationPort.class);
        CourseEnrollmentConversationListener listener = new CourseEnrollmentConversationListener(courseConversationPort);

        listener.onActivated(new CourseEnrollmentActivatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(courseConversationPort);
    }
}

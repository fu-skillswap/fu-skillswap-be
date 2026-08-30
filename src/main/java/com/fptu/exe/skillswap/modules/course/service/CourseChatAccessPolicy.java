package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.booking.service.BookingChatAccessPolicy.Access;
import com.fptu.exe.skillswap.modules.chat.domain.ChatReadOnlyReason;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipantAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CourseChatAccessPolicy {

    private final CourseEnrollmentRepository enrollmentRepository;
    private final ConversationParticipantRepository participantRepository;

    public Access resolve(Conversation conversation, UUID userId) {
        if (conversation == null || userId == null) {
            return Access.readOnly(ChatReadOnlyReason.NO_EFFECTIVE_BOOKING);
        }
        if (conversation.getStatus() == ConversationStatus.LOCKED) {
            return Access.readOnly(ChatReadOnlyReason.ADMIN_LOCKED);
        }

        // Course Mentor has permanent full access
        if (userId.equals(conversation.getMentorUserId())) {
            return Access.open(null, true);
        }

        // Verify participant record state
        Optional<ConversationParticipant> participantOpt = participantRepository
                .findByConversationIdAndUserId(conversation.getId(), userId);
        if (participantOpt.isEmpty() || participantOpt.get().getAccessState() == ConversationParticipantAccess.REVOKED) {
            return Access.readOnly(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED);
        }

        // Verify active or completed course enrollment
        UUID courseId = conversation.getSourceId();
        Optional<CourseEnrollment> enrollmentOpt = enrollmentRepository.findByCourseIdAndStudentUserId(courseId, userId);
        if (enrollmentOpt.isEmpty()) {
            return Access.readOnly(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED);
        }

        CourseEnrollment enrollment = enrollmentOpt.get();
        if (enrollment.getStatus() == EnrollmentStatus.ACTIVE || enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            return Access.open(null, true);
        }

        return Access.readOnly(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED);
    }
}

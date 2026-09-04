package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.ChatMessagingAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ChatReadOnlyReason;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipantAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.chat.repository.CourseConversationContextRepository;
import com.fptu.exe.skillswap.modules.chat.domain.CourseConversationContext;
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseChatAccessPolicyTest {

    @Mock
    private CourseQueryPort courseQueryPort;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private CourseConversationContextRepository contextRepository;

    @InjectMocks
    private CourseChatAccessPolicy accessPolicy;

    private UUID mentorId;
    private UUID studentId;
    private UUID courseId;
    private UUID conversationId;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        mentorId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        conversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.COURSE)
                .sourceId(courseId)
                .mentorUserId(mentorId)
                .type(ConversationType.GROUP)
                .status(ConversationStatus.ACTIVE)
                .build();
    }

    @Test
    void testMentorHasPermanentOpenAccess() {
        var access = accessPolicy.resolve(conversation, mentorId);
        assertEquals(ChatMessagingAccess.OPEN, access.messagingAccess());
        assertTrue(access.canSendMessages());
        assertTrue(access.postSessionChatPermanent());
    }

    @Test
    void testActiveStudentHasOpenAccess() {
        ConversationParticipant participant = ConversationParticipant.builder()
                .accessState(ConversationParticipantAccess.ACTIVE)
                .build();
        when(participantRepository.findByConversationIdAndUserId(conversationId, studentId))
                .thenReturn(Optional.of(participant));

        when(courseQueryPort.isUserEnrolledInCourse(courseId, studentId))
                .thenReturn(true);

        var access = accessPolicy.resolve(conversation, studentId);
        assertEquals(ChatMessagingAccess.OPEN, access.messagingAccess());
        assertTrue(access.canSendMessages());
    }

    @Test
    void testRevokedStudentHasReadOnlyAccess() {
        ConversationParticipant participant = ConversationParticipant.builder()
                .accessState(ConversationParticipantAccess.REVOKED)
                .build();
        when(participantRepository.findByConversationIdAndUserId(conversationId, studentId))
                .thenReturn(Optional.of(participant));

        var access = accessPolicy.resolve(conversation, studentId);
        assertEquals(ChatMessagingAccess.READ_ONLY, access.messagingAccess());
        assertFalse(access.canSendMessages());
        assertEquals(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED, access.readOnlyReason());
    }

    @Test
    void testRefundedStudentHasReadOnlyAccess() {
        ConversationParticipant participant = ConversationParticipant.builder()
                .accessState(ConversationParticipantAccess.ACTIVE)
                .build();
        when(participantRepository.findByConversationIdAndUserId(conversationId, studentId))
                .thenReturn(Optional.of(participant));

        when(courseQueryPort.isUserEnrolledInCourse(courseId, studentId))
                .thenReturn(false);

        var access = accessPolicy.resolve(conversation, studentId);
        assertEquals(ChatMessagingAccess.READ_ONLY, access.messagingAccess());
        assertFalse(access.canSendMessages());
        assertEquals(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED, access.readOnlyReason());
    }

    @Test
    void testLockedConversationIsReadOnly() {
        conversation.setStatus(ConversationStatus.LOCKED);
        var access = accessPolicy.resolve(conversation, mentorId);
        assertEquals(ChatMessagingAccess.READ_ONLY, access.messagingAccess());
        assertEquals(ChatReadOnlyReason.ADMIN_LOCKED, access.readOnlyReason());
    }

    @Test
    void revokedCourseEnrollmentHasReadOnlyAccess() {
        UUID contextId = UUID.randomUUID();
        Conversation directCourseConversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.COURSE)
                .sourceId(contextId)
                .type(ConversationType.DIRECT)
                .status(ConversationStatus.ACTIVE)
                .build();
        when(contextRepository.findByConversationId(conversationId)).thenReturn(Optional.of(
                CourseConversationContext.builder()
                        .id(contextId)
                        .courseId(courseId)
                        .menteeUserId(studentId)
                        .conversationId(conversationId)
                        .build()));
        when(courseQueryPort.findCourseChatContext(courseId))
                .thenReturn(Optional.of(new CourseQueryPort.CourseChatContext(courseId, mentorId)));
        when(participantRepository.findByConversationIdAndUserId(conversationId, studentId))
                .thenReturn(Optional.of(ConversationParticipant.builder()
                        .accessState(ConversationParticipantAccess.ACTIVE)
                        .build()));
        when(courseQueryPort.isUserEnrolledInCourse(courseId, studentId)).thenReturn(false);

        var access = accessPolicy.resolve(directCourseConversation, studentId);

        assertFalse(access.canSendMessages());
        assertEquals(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED, access.readOnlyReason());
    }

    @Test
    void nonParticipantCannotAccessCourseConversationEvenWhenEnrolledElsewhere() {
        UUID contextId = UUID.randomUUID();
        Conversation directCourseConversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.COURSE)
                .sourceId(contextId)
                .type(ConversationType.DIRECT)
                .status(ConversationStatus.ACTIVE)
                .build();
        when(contextRepository.findByConversationId(conversationId)).thenReturn(Optional.of(
                CourseConversationContext.builder()
                        .id(contextId)
                        .courseId(courseId)
                        .menteeUserId(studentId)
                        .conversationId(conversationId)
                        .build()));
        when(courseQueryPort.findCourseChatContext(courseId))
                .thenReturn(Optional.of(new CourseQueryPort.CourseChatContext(courseId, mentorId)));
        when(participantRepository.findByConversationIdAndUserId(conversationId, studentId))
                .thenReturn(Optional.empty());

        var access = accessPolicy.resolve(directCourseConversation, studentId);

        assertFalse(access.canSendMessages());
        assertEquals(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED, access.readOnlyReason());
    }
}

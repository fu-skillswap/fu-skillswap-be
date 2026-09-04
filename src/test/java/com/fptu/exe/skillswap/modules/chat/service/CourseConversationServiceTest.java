package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.domain.CourseConversationContext;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.chat.repository.CourseConversationContextRepository;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.mentor.port.MentorOwnershipQueryPort;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Captor;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseConversationServiceTest {

    @Mock
    private CourseConversationContextRepository contextRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private CourseEnrollmentRepository enrollmentRepository;
    @Mock
    private CourseQueryPort courseQueryPort;
    @Mock
    private UserQueryPort userQueryPort;
    @Mock
    private MentorOwnershipQueryPort mentorOwnershipQueryPort;
    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private ConversationService conversationService;
    @Captor
    private ArgumentCaptor<Conversation> conversationCaptor;

    private CourseConversationService service;
    private UUID courseId;
    private UUID secondCourseId;
    private UUID menteeId;
    private UUID mentorId;
    private User mentee;
    private User mentor;

    @BeforeEach
    void setUp() {
        service = new CourseConversationService(
                contextRepository,
                conversationRepository,
                enrollmentRepository,
                courseQueryPort,
                userQueryPort,
                mentorOwnershipQueryPort,
                chatRoomService,
                conversationService,
                null
        );

        courseId = UUID.randomUUID();
        secondCourseId = UUID.randomUUID();
        menteeId = UUID.randomUUID();
        mentorId = UUID.randomUUID();
        mentee = User.builder().id(menteeId).email("mentee@example.com").fullName("Mentee").build();
        mentor = User.builder().id(mentorId).email("mentor@example.com").fullName("Mentor").build();

        lenient().when(userQueryPort.findUserSummaryById(menteeId)).thenReturn(Optional.of(
                summary(menteeId, "Mentee", Set.of(RoleCode.MENTEE))));
        lenient().when(userQueryPort.findUserById(menteeId)).thenReturn(Optional.of(mentee));
        lenient().when(userQueryPort.findUserSummaryById(mentorId)).thenReturn(Optional.of(
                summary(mentorId, "Mentor", Set.of(RoleCode.MENTOR))));
        lenient().when(userQueryPort.findUserById(mentorId)).thenReturn(Optional.of(mentor));
        lenient().when(mentorOwnershipQueryPort.isActiveOwner(mentorId, mentorId)).thenReturn(true);
        lenient().when(enrollmentRepository.findByCourseIdAndStudentUserId(courseId, menteeId))
                .thenReturn(Optional.of(activeEnrollment()));
        lenient().when(courseQueryPort.findCourseChatContext(courseId))
                .thenReturn(Optional.of(new CourseQueryPort.CourseChatContext(courseId, mentorId)));
        lenient().when(courseQueryPort.findCourseTitleById(courseId)).thenReturn(Optional.of("Course A"));
        lenient().when(conversationService.getConversationDetail(any(), eq(menteeId)))
                .thenAnswer(invocation -> ConversationResponse.builder()
                        .id(invocation.getArgument(0))
                        .type(ConversationType.DIRECT)
                        .build());
        lenient().when(conversationRepository.saveAndFlush(any(Conversation.class)))
                .thenAnswer(invocation -> {
                    Conversation conversation = invocation.getArgument(0);
                    conversation.setId(UUID.randomUUID());
                    return conversation;
                });
        lenient().when(contextRepository.saveAndFlush(any(CourseConversationContext.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void firstRequestCreatesOneCourseScopedConversation() {
        var response = service.getOrCreate(courseId, menteeId);
        Conversation conversation = savedConversation();

        assertEquals(courseId, response.courseId());
        assertEquals("Course A", response.courseTitle());
        assertEquals("COURSE_DIRECT", response.contextType());
        assertEquals(mentorId, response.mentorUserId());
        assertEquals(ConversationSourceType.COURSE, conversation.getSourceType());
        assertEquals(ConversationType.DIRECT, conversation.getType());
        assertNotEquals(courseId, conversation.getSourceId());
        assertEquals(null, conversation.getMentorUserId());
        assertEquals(null, conversation.getMenteeUserId());
        verify(chatRoomService).addParticipantIfAbsent(conversation, mentor);
        verify(chatRoomService).addParticipantIfAbsent(conversation, mentee);
    }

    @Test
    void secondRequestReturnsTheExistingContext() {
        UUID conversationId = UUID.randomUUID();
        CourseConversationContext existing = CourseConversationContext.builder()
                .id(UUID.randomUUID())
                .courseId(courseId)
                .menteeUserId(menteeId)
                .conversationId(conversationId)
                .build();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.COURSE)
                .sourceId(existing.getId())
                .type(ConversationType.DIRECT)
                .build();
        when(contextRepository.findByCourseIdAndMenteeUserId(courseId, menteeId)).thenReturn(Optional.of(existing));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        var response = service.getOrCreate(courseId, menteeId);

        assertEquals(conversationId, response.conversationId());
        verify(conversationRepository, never()).saveAndFlush(any(Conversation.class));
        verify(contextRepository, never()).saveAndFlush(any(CourseConversationContext.class));
    }

    @Test
    void sameUsersInDifferentCoursesCreateDifferentContexts() {
        when(contextRepository.findByCourseIdAndMenteeUserId(courseId, menteeId)).thenReturn(Optional.empty());
        when(contextRepository.findByCourseIdAndMenteeUserId(secondCourseId, menteeId)).thenReturn(Optional.empty());
        when(enrollmentRepository.findByCourseIdAndStudentUserId(secondCourseId, menteeId))
                .thenReturn(Optional.of(activeEnrollment()));
        when(courseQueryPort.findCourseChatContext(secondCourseId))
                .thenReturn(Optional.of(new CourseQueryPort.CourseChatContext(secondCourseId, mentorId)));
        when(courseQueryPort.findCourseTitleById(secondCourseId)).thenReturn(Optional.of("Course B"));
        when(conversationRepository.saveAndFlush(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            conversation.setId(UUID.randomUUID());
            return conversation;
        });

        var first = service.getOrCreate(courseId, menteeId);
        var second = service.getOrCreate(secondCourseId, menteeId);

        assertNotEquals(first.conversationId(), second.conversationId());
        assertEquals(courseId, first.courseId());
        assertEquals(secondCourseId, second.courseId());
    }

    @Test
    void nonEnrolledUserIsDenied() {
        when(enrollmentRepository.findByCourseIdAndStudentUserId(courseId, menteeId)).thenReturn(Optional.empty());

        assertThrows(BaseException.class, () -> service.getOrCreate(courseId, menteeId));
        verify(conversationRepository, never()).saveAndFlush(any(Conversation.class));
    }

    @Test
    void inactiveUserIsDenied() {
        when(userQueryPort.findUserSummaryById(menteeId)).thenReturn(Optional.of(
                new UserSummaryRecord(menteeId, "mentee@example.com", "Mentee", null,
                        Set.of(RoleCode.MENTEE), "INACTIVE", false)));

        assertThrows(BaseException.class, () -> service.getOrCreate(courseId, menteeId));
        verify(enrollmentRepository, never()).findByCourseIdAndStudentUserId(any(), any());
    }

    @Test
    void removedMentorIsDenied() {
        when(mentorOwnershipQueryPort.isActiveOwner(mentorId, mentorId)).thenReturn(false);

        assertThrows(BaseException.class, () -> service.getOrCreate(courseId, menteeId));
        verify(conversationRepository, never()).saveAndFlush(any(Conversation.class));
    }

    private Conversation savedConversation() {
        verify(conversationRepository).saveAndFlush(conversationCaptor.capture());
        return conversationCaptor.getValue();
    }

    private CourseEnrollment activeEnrollment() {
        return CourseEnrollment.builder().status(EnrollmentStatus.ACTIVE).build();
    }

    private UserSummaryRecord summary(UUID id, String name, Set<RoleCode> roles) {
        return new UserSummaryRecord(id, id + "@example.com", name, null, roles, "ACTIVE", true);
    }

}

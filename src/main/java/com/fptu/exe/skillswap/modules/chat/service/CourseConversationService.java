package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.domain.CourseConversationContext;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.chat.dto.response.CourseConversationResponse;
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
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseConversationService {

    private static final String COURSE_DIRECT_CONTEXT_TYPE = "COURSE_DIRECT";

    private final CourseConversationContextRepository contextRepository;
    private final ConversationRepository conversationRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseQueryPort courseQueryPort;
    private final UserQueryPort userQueryPort;
    private final MentorOwnershipQueryPort mentorOwnershipQueryPort;
    private final ChatRoomService chatRoomService;
    private final ConversationService conversationService;
    private final PlatformTransactionManager transactionManager;

    public CourseConversationResponse getOrCreate(UUID courseId, UUID currentUserId) {
        CourseChatActors actors = authorize(courseId, currentUserId);

        try {
            if (transactionManager == null) {
                return getOrCreateInTransaction(actors);
            }
            return new TransactionTemplate(transactionManager)
                    .execute(status -> getOrCreateInTransaction(actors));
        } catch (DataIntegrityViolationException exception) {
            // A concurrent request may have won the course/mentee unique constraint.
            CourseConversationContext existing = contextRepository
                    .findByCourseIdAndMenteeUserId(courseId, currentUserId)
                    .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT,
                            "Không thể tạo cuộc hội thoại khóa học", exception));
            return toResponse(existing, actors);
        }
    }

    private CourseConversationResponse getOrCreateInTransaction(CourseChatActors actors) {
        CourseConversationContext existing = contextRepository
                .findByCourseIdAndMenteeUserId(actors.courseId(), actors.menteeUserId())
                .orElse(null);
        if (existing != null) {
            Conversation conversation = conversationRepository.findById(existing.getConversationId())
                    .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT,
                            "Course chat context không còn conversation tương ứng"));
            ensureParticipants(conversation, actors);
            return toResponse(existing, actors);
        }

        UUID contextId = UUID.randomUUID();
        Conversation conversation = conversationRepository.saveAndFlush(Conversation.builder()
                .sourceType(ConversationSourceType.COURSE)
                .sourceId(contextId)
                .type(ConversationType.DIRECT)
                .status(ConversationStatus.ACTIVE)
                // Course context owns the participant identity. Do not populate the
                // legacy global mentor/mentee pair identity fields.
                .build());

        ensureParticipants(conversation, actors);

        CourseConversationContext context = contextRepository.saveAndFlush(CourseConversationContext.builder()
                .id(contextId)
                .courseId(actors.courseId())
                .menteeUserId(actors.menteeUserId())
                .conversationId(conversation.getId())
                .build());

        return toResponse(context, actors);
    }

    private CourseChatActors authorize(UUID courseId, UUID currentUserId) {
        if (courseId == null || currentUserId == null) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Course chat context không hợp lệ");
        }

        UserSummaryRecord menteeSummary = userQueryPort.findUserSummaryById(currentUserId)
                .filter(UserSummaryRecord::isActive)
                .orElseThrow(() -> new BaseException(ErrorCode.ACCESS_DENIED,
                        "Tài khoản không hoạt động không được sử dụng chat"));
        User mentee = userQueryPort.findUserById(currentUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        CourseQueryPort.CourseChatContext course = courseQueryPort.findCourseChatContext(courseId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy khóa học"));

        CourseEnrollment enrollment = enrollmentRepository
                .findByCourseIdAndStudentUserId(courseId, currentUserId)
                .filter(value -> value.getStatus() == EnrollmentStatus.ACTIVE)
                .orElseThrow(() -> new BaseException(ErrorCode.ACCESS_DENIED,
                        "Chỉ học viên đang active mới được mở chat khóa học"));

        UUID mentorUserId = course.mentorUserId();
        UserSummaryRecord mentorSummary = mentorUserId == null
                ? null
                : userQueryPort.findUserSummaryById(mentorUserId).orElse(null);
        User mentor = mentorUserId == null
                ? null
                : userQueryPort.findUserById(mentorUserId).orElse(null);
        if (mentorSummary == null
                || mentor == null
                || !mentorSummary.isActive()
                || !mentorSummary.hasRole(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR)
                || !mentorOwnershipQueryPort.isActiveOwner(mentorUserId, mentorUserId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED,
                    "Course mentor hiện không còn đủ điều kiện chat");
        }

        return new CourseChatActors(
                courseId,
                currentUserId,
                mentee,
                mentorUserId,
                mentor,
                mentorSummary,
                courseQueryPort.findCourseTitleById(courseId).orElse(null),
                enrollment
        );
    }

    private void ensureParticipants(Conversation conversation, CourseChatActors actors) {
        chatRoomService.addParticipantIfAbsent(conversation, actors.mentor());
        chatRoomService.addParticipantIfAbsent(conversation, actors.mentee());
    }

    private CourseConversationResponse toResponse(CourseConversationContext context, CourseChatActors actors) {
        ConversationResponse conversationResponse = conversationService
                .getConversationDetail(context.getConversationId(), actors.menteeUserId());
        return new CourseConversationResponse(
                context.getConversationId(),
                context.getCourseId(),
                actors.courseTitle(),
                COURSE_DIRECT_CONTEXT_TYPE,
                actors.mentorUserId(),
                actors.mentorSummary().fullName(),
                actors.mentorSummary().avatarUrl(),
                conversationResponse
        );
    }

    private record CourseChatActors(
            UUID courseId,
            UUID menteeUserId,
            User mentee,
            UUID mentorUserId,
            User mentor,
            UserSummaryRecord mentorSummary,
            String courseTitle,
            CourseEnrollment enrollment
    ) {
    }
}

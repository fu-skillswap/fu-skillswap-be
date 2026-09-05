package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationBookingLink;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipantAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipantRole;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationBookingLinkRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationBookingLinkRepository conversationBookingLinkRepository;
    private final CourseQueryPort courseQueryPort;
    private final UserQueryPort userQueryPort;

    @Transactional
    public Conversation createDirectForAcceptedBooking(UUID bookingId, UUID mentorUserId, UUID menteeUserId) {
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking must not be null");
        }

        if (mentorUserId == null || menteeUserId == null) {
            throw new IllegalArgumentException("Booking must have mentor and mentee users");
        }
        User mentorUser = userQueryPort.findUserById(mentorUserId).orElseThrow();
        User menteeUser = userQueryPort.findUserById(menteeUserId).orElseThrow();

        Conversation conversation = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, bookingId)
                .orElseGet(() -> conversationRepository.findDirectActiveByParticipantPair(
                        mentorUser.getId(), menteeUser.getId(), ConversationType.DIRECT, ConversationStatus.ACTIVE)
                        .stream().findFirst().orElse(null));
        if (conversation == null) {
            try {
                conversation = conversationRepository.save(Conversation.builder()
                        .sourceType(ConversationSourceType.BOOKING)
                        .sourceId(bookingId)
                        .mentorUserId(mentorUser.getId())
                        .menteeUserId(menteeUser.getId())
                        .type(ConversationType.DIRECT)
                        .status(ConversationStatus.ACTIVE)
                        .build());
            } catch (DataIntegrityViolationException ignored) {
                conversation = null;
            }
        }
        if (conversation == null) {
            conversation = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, bookingId)
                    .orElseGet(() -> conversationRepository.findDirectActiveByParticipantPair(
                            mentorUser.getId(), menteeUser.getId(), ConversationType.DIRECT, ConversationStatus.ACTIVE)
                            .stream().findFirst()
                            .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT,
                                    "Không thể tạo cuộc hội thoại trực tiếp")));
        }

        addParticipantIfAbsent(conversation, mentorUser);
        addParticipantIfAbsent(conversation, menteeUser);
        if (conversationBookingLinkRepository != null && !conversationBookingLinkRepository.existsByBookingId(bookingId)) {
            conversationBookingLinkRepository.save(ConversationBookingLink.builder()
                    .conversation(conversation)
                    .bookingId(bookingId)
                    .build());
        }
        return conversation;
    }

    @Transactional
    public void addParticipantIfAbsent(Conversation conversation, User user) {
        if (!participantRepository.existsByConversationIdAndUserId(conversation.getId(), user.getId())) {
            ConversationParticipant participant = ConversationParticipant.builder()
                    .conversation(conversation)
                    .user(user)
                    .joinedAt(DateTimeUtil.now())
                    .build();
            try {
                participantRepository.save(participant);
            } catch (DataIntegrityViolationException ignored) {
            }
        }
    }

    @Transactional(readOnly = true)
    public Conversation findByBookingId(UUID bookingId) {
        return conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, bookingId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Conversation findById(UUID conversationId) {
        return conversationRepository.findById(conversationId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Conversation findDirectByParticipants(UUID firstUserId, UUID secondUserId) {
        if (firstUserId == null || secondUserId == null) {
            return null;
        }
        List<Conversation> conversations = conversationRepository.findDirectActiveByParticipantPair(
                firstUserId,
                secondUserId,
                ConversationType.DIRECT,
                ConversationStatus.ACTIVE
        );
        return conversations == null || conversations.isEmpty() ? null : conversations.getFirst();
    }

    @Transactional
    public Conversation ensureCourseGroupConversation(CourseQueryPort.CourseChatContext course) {
        if (course == null || course.courseId() == null) {
            throw new IllegalArgumentException("Course must not be null");
        }
        UUID mentorUserId = course.mentorUserId();
        if (mentorUserId == null) {
            throw new IllegalArgumentException("Course must have mentor user");
        }
        User mentorUser = userQueryPort.findUserById(mentorUserId).orElseThrow();

        Conversation conversation = conversationRepository
                .findBySourceTypeAndSourceId(ConversationSourceType.COURSE, course.courseId())
                .orElse(null);
        if (conversation == null) {
            try {
                conversation = conversationRepository.save(Conversation.builder()
                        .sourceType(ConversationSourceType.COURSE)
                        .sourceId(course.courseId())
                        .mentorUserId(mentorUser.getId())
                        .menteeUserId(null)
                        .type(ConversationType.GROUP)
                        .status(ConversationStatus.ACTIVE)
                        .build());
            } catch (DataIntegrityViolationException ex) {
                conversation = conversationRepository
                        .findBySourceTypeAndSourceId(ConversationSourceType.COURSE, course.courseId())
                        .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể tạo nhóm chat khóa học"));
            }
        }

        addGroupParticipantIfAbsent(conversation, mentorUser,
                ConversationParticipantRole.MENTOR,
                ConversationParticipantAccess.ACTIVE);
        return conversation;
    }

    @Transactional
    public void addCourseStudentParticipant(UUID courseId, UUID studentUserId) {
        if (courseId == null || studentUserId == null) {
            return;
        }
        var course = courseQueryPort.findCourseChatContext(courseId).orElse(null);
        if (course == null) {
            return;
        }

        Conversation conversation = ensureCourseGroupConversation(course);

        User studentUser = resolveUser(studentUserId);
        if (studentUser == null) {
            return;
        }

        var existingOpt = participantRepository.findByConversationIdAndUserId(conversation.getId(), studentUserId);
        if (existingOpt.isPresent()) {
            ConversationParticipant participant = existingOpt.get();
            if (participant.getAccessState() != ConversationParticipantAccess.ACTIVE) {
                participant.setAccessState(ConversationParticipantAccess.ACTIVE);
                participantRepository.save(participant);
            }
        } else {
            addGroupParticipantIfAbsent(conversation, studentUser,
                    ConversationParticipantRole.ATTENDEE,
                    ConversationParticipantAccess.ACTIVE);
        }
    }

    @Transactional
    public void revokeCourseStudentParticipant(UUID courseId, UUID studentUserId) {
        if (courseId == null || studentUserId == null) {
            return;
        }
        Conversation conversation = conversationRepository
                .findBySourceTypeAndSourceId(ConversationSourceType.COURSE, courseId)
                .orElse(null);
        if (conversation == null) {
            return;
        }
        participantRepository.findByConversationIdAndUserId(conversation.getId(), studentUserId)
                .ifPresent(participant -> {
                    participant.setAccessState(ConversationParticipantAccess.REVOKED);
                    participantRepository.save(participant);
                });
    }

    public void addGroupParticipantIfAbsent(Conversation conversation, User user,
                                            ConversationParticipantRole role,
                                            ConversationParticipantAccess access) {
        if (!participantRepository.existsByConversationIdAndUserId(conversation.getId(), user.getId())) {
            try {
                participantRepository.saveAndFlush(ConversationParticipant.builder()
                        .conversation(conversation).user(user).joinedAt(DateTimeUtil.now())
                        .participantRole(role).accessState(access).build());
            } catch (DataIntegrityViolationException ignored) {
            }
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> getActiveRecipientUserIds(UUID conversationId, UUID senderId) {
        List<UUID> participantIds = participantRepository.findByConversationId(conversationId).stream()
                .filter(p -> p.getAccessState() != ConversationParticipantAccess.REVOKED)
                .map(p -> p.getUser().getId())
                .filter(java.util.Objects::nonNull)
                .toList();
        Set<UUID> activeUserIds = activeUserIds(participantIds);
        return participantIds.stream()
                .filter(activeUserIds::contains)
                .filter(id -> senderId == null || !id.equals(senderId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> getConversationParticipantUserIds(UUID conversationId) {
        List<UUID> participantIds = participantRepository.findByConversationId(conversationId).stream()
                .filter(p -> p.getAccessState() != ConversationParticipantAccess.REVOKED)
                .map(p -> p.getUser().getId())
                .filter(java.util.Objects::nonNull)
                .toList();
        Set<UUID> activeUserIds = activeUserIds(participantIds);
        return participantIds.stream()
                .filter(activeUserIds::contains)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return userQueryPort.isUserActive(userId)
                && participantRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    public void ensureParticipant(UUID conversationId, UUID userId) {
        if (!userQueryPort.isUserActive(userId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Tài khoản không hoạt động không được sử dụng chat");
        }
        var participant = participantRepository.findByConversationIdAndUserId(conversationId, userId);
        if (participant.isEmpty() || participant.get().getAccessState() == ConversationParticipantAccess.REVOKED) {
            throw new BaseException(ErrorCode.CHAT_ACCESS_DENIED);
        }
    }

    private User resolveUser(UUID userId) {
        if (userId == null) return null;
        return userQueryPort.findUserById(userId).orElse(null);
    }

    private Set<UUID> activeUserIds(List<UUID> userIds) {
        return userQueryPort.findUsersByIdIn(userIds).stream()
                .filter(user -> user.getStatus() == com.fptu.exe.skillswap.modules.identity.domain.UserStatus.ACTIVE)
                .map(User::getId)
                .collect(Collectors.toSet());
    }
}

package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
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
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationBookingLinkRepository conversationBookingLinkRepository;
    private final ObjectProvider<com.fptu.exe.skillswap.modules.course.repository.CourseRepository> courseRepositoryProvider;
    private final ObjectProvider<UserQueryPort> userQueryPortProvider;
    private final ObjectProvider<com.fptu.exe.skillswap.modules.identity.repository.UserRepository> userRepositoryProvider;

    @Transactional
    public Conversation createDirectForAcceptedBooking(Booking booking) {
        if (booking == null || booking.getId() == null) {
            throw new IllegalArgumentException("Booking must not be null");
        }

        User mentorUser = booking.getMentorProfile() == null ? null : booking.getMentorProfile().getUser();
        User menteeUser = booking.getMentee();
        if (mentorUser == null || mentorUser.getId() == null || menteeUser == null || menteeUser.getId() == null) {
            throw new IllegalArgumentException("Booking must have mentor and mentee users");
        }

        Conversation conversation = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, booking.getId())
                .or(() -> conversationRepository.findByMentorUserIdAndMenteeUserId(mentorUser.getId(), menteeUser.getId()))
                .orElse(null);
        if (conversation == null) {
            try {
                conversation = conversationRepository.save(Conversation.builder()
                        .sourceType(ConversationSourceType.BOOKING)
                        .sourceId(booking.getId())
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
            conversation = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, booking.getId())
                    .or(() -> conversationRepository.findByMentorUserIdAndMenteeUserId(mentorUser.getId(), menteeUser.getId()))
                    .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể tạo cuộc hội thoại trực tiếp"));
        }

        addParticipantIfAbsent(conversation, mentorUser);
        addParticipantIfAbsent(conversation, menteeUser);
        if (conversationBookingLinkRepository != null && !conversationBookingLinkRepository.existsByBookingId(booking.getId())) {
            conversationBookingLinkRepository.save(ConversationBookingLink.builder()
                    .conversation(conversation)
                    .booking(booking)
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
    public Conversation ensureCourseGroupConversation(com.fptu.exe.skillswap.modules.course.domain.Course course) {
        if (course == null || course.getId() == null) {
            throw new IllegalArgumentException("Course must not be null");
        }
        User mentorUser = course.getMentorProfile() == null ? null : course.getMentorProfile().getUser();
        if (mentorUser == null || mentorUser.getId() == null) {
            throw new IllegalArgumentException("Course must have mentor user");
        }

        Conversation conversation = conversationRepository
                .findBySourceTypeAndSourceId(ConversationSourceType.COURSE, course.getId())
                .orElse(null);
        if (conversation == null) {
            try {
                conversation = conversationRepository.save(Conversation.builder()
                        .sourceType(ConversationSourceType.COURSE)
                        .sourceId(course.getId())
                        .mentorUserId(mentorUser.getId())
                        .menteeUserId(null)
                        .type(ConversationType.GROUP)
                        .status(ConversationStatus.ACTIVE)
                        .build());
            } catch (DataIntegrityViolationException ex) {
                conversation = conversationRepository
                        .findBySourceTypeAndSourceId(ConversationSourceType.COURSE, course.getId())
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
        var courseRepo = courseRepositoryProvider.getIfAvailable();
        com.fptu.exe.skillswap.modules.course.domain.Course course = courseRepo != null
                ? courseRepo.findById(courseId).orElse(null)
                : null;
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
        return participantRepository.findByConversationId(conversationId).stream()
                .filter(p -> p.getAccessState() != ConversationParticipantAccess.REVOKED)
                .map(p -> p.getUser().getId())
                .filter(id -> senderId == null || !id.equals(senderId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> getConversationParticipantUserIds(UUID conversationId) {
        return participantRepository.findByConversationId(conversationId).stream()
                .map(p -> p.getUser().getId())
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return participantRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    public void ensureParticipant(UUID conversationId, UUID userId) {
        var participant = participantRepository.findByConversationIdAndUserId(conversationId, userId);
        if (participant.isEmpty() || participant.get().getAccessState() == ConversationParticipantAccess.REVOKED) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập vào cuộc hội thoại này");
        }
    }

    private User resolveUser(UUID userId) {
        if (userId == null) return null;
        var userPort = userQueryPortProvider.getIfAvailable();
        if (userPort != null) {
            return userPort.findUserById(userId).orElse(null);
        }
        var userRepo = userRepositoryProvider.getIfAvailable();
        return userRepo != null ? userRepo.findById(userId).orElse(null) : null;
    }
}

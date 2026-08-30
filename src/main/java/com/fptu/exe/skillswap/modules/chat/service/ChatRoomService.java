package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.*;
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

import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationBookingLinkRepository conversationBookingLinkRepository;
    private final ObjectProvider<UserQueryPort> userQueryPortProvider;

    @Transactional
    public Conversation createDirectForAcceptedBooking(UUID bookingId, UUID mentorUserId, UUID menteeUserId) {
        if (bookingId == null || mentorUserId == null || menteeUserId == null) {
            throw new IllegalArgumentException("Booking ID, mentor user ID and mentee user ID must not be null");
        }

        Conversation conversation = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, bookingId)
                .or(() -> conversationRepository.findByMentorUserIdAndMenteeUserId(mentorUserId, menteeUserId))
                .orElse(null);
        if (conversation == null) {
            try {
                conversation = conversationRepository.save(Conversation.builder()
                        .sourceType(ConversationSourceType.BOOKING)
                        .sourceId(bookingId)
                        .mentorUserId(mentorUserId)
                        .menteeUserId(menteeUserId)
                        .type(ConversationType.DIRECT)
                        .status(ConversationStatus.ACTIVE)
                        .build());
            } catch (DataIntegrityViolationException ignored) {
                conversation = null;
            }
        }
        if (conversation == null) {
            conversation = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, bookingId)
                    .or(() -> conversationRepository.findByMentorUserIdAndMenteeUserId(mentorUserId, menteeUserId))
                    .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể tạo cuộc hội thoại trực tiếp"));
        }

        User mentorUser = resolveUser(mentorUserId);
        User menteeUser = resolveUser(menteeUserId);
        if (mentorUser != null) addParticipantIfAbsent(conversation, mentorUser);
        if (menteeUser != null) addParticipantIfAbsent(conversation, menteeUser);

        Conversation finalConversation = conversation;
        conversationBookingLinkRepository.findByConversationId(conversation.getId()).stream()
                .filter(link -> bookingId.equals(link.getBookingId()))
                .findFirst()
                .orElseGet(() -> conversationBookingLinkRepository.save(
                        ConversationBookingLink.builder()
                                .conversation(finalConversation)
                                .bookingId(bookingId)
                                .linkedAt(DateTimeUtil.now())
                                .build()
                ));

        return conversation;
    }

    @Transactional
    public void addParticipantIfAbsent(Conversation conversation, User user) {
        if (conversation == null || user == null) return;
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

    @Transactional(readOnly = true)
    public Map<UUID, UUID> getConversationIdsForBookings(Collection<UUID> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) return Map.of();
        List<ConversationBookingLink> links = conversationBookingLinkRepository.findByBookingIdIn(bookingIds);
        Map<UUID, UUID> map = new HashMap<>();
        for (ConversationBookingLink link : links) {
            if (link.getConversation() != null && link.getBookingId() != null) {
                map.put(link.getBookingId(), link.getConversation().getId());
            }
        }
        return map;
    }

    @Transactional
    public Conversation ensureCourseGroupConversation(UUID courseId, UUID mentorUserId) {
        if (courseId == null || mentorUserId == null) {
            throw new IllegalArgumentException("Course ID and mentor user ID must not be null");
        }

        Conversation conversation = conversationRepository
                .findBySourceTypeAndSourceId(ConversationSourceType.COURSE, courseId)
                .orElse(null);
        if (conversation == null) {
            try {
                conversation = conversationRepository.save(Conversation.builder()
                        .sourceType(ConversationSourceType.COURSE)
                        .sourceId(courseId)
                        .mentorUserId(mentorUserId)
                        .menteeUserId(null)
                        .type(ConversationType.GROUP)
                        .status(ConversationStatus.ACTIVE)
                        .build());
            } catch (DataIntegrityViolationException ex) {
                conversation = conversationRepository
                        .findBySourceTypeAndSourceId(ConversationSourceType.COURSE, courseId)
                        .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể tạo nhóm chat khóa học"));
            }
        }

        User mentorUser = resolveUser(mentorUserId);
        if (mentorUser != null) {
            addGroupParticipantIfAbsent(conversation, mentorUser,
                    ConversationParticipantRole.MENTOR,
                    ConversationParticipantAccess.ACTIVE);
        }
        return conversation;
    }

    @Transactional
    public void addCourseStudentParticipant(UUID courseId, UUID studentUserId) {
        if (courseId == null || studentUserId == null) {
            return;
        }
        Conversation conversation = conversationRepository
                .findBySourceTypeAndSourceId(ConversationSourceType.COURSE, courseId)
                .orElse(null);
        if (conversation != null) {
            addCourseStudentParticipant(courseId, conversation.getMentorUserId(), studentUserId);
        }
    }

    @Transactional
    public void addCourseStudentParticipant(UUID courseId, UUID mentorUserId, UUID studentUserId) {
        if (courseId == null || studentUserId == null) {
            return;
        }
        Conversation conversation = ensureCourseGroupConversation(courseId, mentorUserId);
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
        var userPort = userQueryPortProvider != null ? userQueryPortProvider.getIfAvailable() : null;
        return userPort != null ? userPort.findUserById(userId).orElse(null) : null;
    }
}

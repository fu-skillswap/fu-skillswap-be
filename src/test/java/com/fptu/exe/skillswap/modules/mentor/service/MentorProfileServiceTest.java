package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorSubjectResult;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorProfileUpsertRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorSubjectResultRequest;
import com.fptu.exe.skillswap.modules.mentor.event.MentorAvailabilityChangedEvent;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorFeaturedProjectRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorSubjectResultRepository;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorProfileServiceTest {

    @Mock private MentorProfileRepository mentorProfileRepository;
    @Mock private MentorTagRepository mentorTagRepository;
    @Mock private TagRepository tagRepository;
    @Mock private UserRepository userRepository;
    @Mock private MentorSubjectResultRepository mentorSubjectResultRepository;
    @Mock private MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    @Mock private MentorAchievementRepository mentorAchievementRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private MentorProfileService mentorProfileService;

    private UUID mentorUserId;
    private UUID helpTopicId;
    private MentorProfile profile;

    @BeforeEach
    void setUp() {
        mentorProfileService = new MentorProfileService(
                mentorProfileRepository,
                mentorTagRepository,
                tagRepository,
                userRepository,
                mentorSubjectResultRepository,
                mentorFeaturedProjectRepository,
                mentorAchievementRepository,
                eventPublisher
        );

        mentorUserId = UuidUtil.generateUuidV7();
        helpTopicId = UuidUtil.generateUuidV7();

        User mentorUser = User.builder()
                .id(mentorUserId)
                .email("mentor@test.com")
                .fullName("Mentor Test")
                .build();

        profile = MentorProfile.builder()
                .userId(mentorUserId)
                .user(mentorUser)
                .isAvailable(true)
                .build();

        when(mentorProfileRepository.findWithUserByUserIdForUpdate(mentorUserId)).thenReturn(Optional.of(profile));
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByIdInAndStatus(any(), any())).thenReturn(List.of(helpTopic()));
        when(mentorSubjectResultRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId))
                .thenReturn(List.of(savedSubjectResult()));
        when(mentorFeaturedProjectRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId))
                .thenReturn(List.of());
        when(mentorAchievementRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId))
                .thenReturn(List.of());
    }

    @Test
    void upsertProfile_publishesAvailabilityChangedEvent_whenAvailabilityTransitions() {
        mentorProfileService.upsertProfile(mentorUserId, validRequest(false));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        MentorAvailabilityChangedEvent event = assertInstanceOf(MentorAvailabilityChangedEvent.class, eventCaptor.getValue());
        assertEquals(mentorUserId, event.mentorUserId());
        assertEquals(mentorUserId, event.mentorProfileId());
        assertEquals(true, event.previousAvailability());
        assertFalse(event.currentAvailability());
    }

    @Test
    void upsertProfile_doesNotPublishAvailabilityEvent_whenAvailabilityDoesNotChange() {
        mentorProfileService.upsertProfile(mentorUserId, validRequest(true));

        verify(eventPublisher, never()).publishEvent(any());
    }

    private MentorProfileUpsertRequest validRequest(Boolean isAvailable) {
        return new MentorProfileUpsertRequest(
                "Backend Mentor",
                "Support peer mentoring for Spring Boot",
                isAvailable,
                List.of(helpTopicId),
                List.of(new MentorSubjectResultRequest("PRJ301", "Java Web", BigDecimal.valueOf(8.5))),
                3,
                3,
                2,
                "https://github.com/mentor",
                "https://portfolio.test",
                "0912345678"
        );
    }

    private Tag helpTopic() {
        return Tag.builder()
                .id(helpTopicId)
                .code("HELP_ACADEMIC_SUPPORT")
                .nameVi("Hỗ trợ môn học")
                .nameEn("Academic Support")
                .type(TagType.HELP_TOPIC)
                .status(TagStatus.ACTIVE)
                .build();
    }

    private MentorSubjectResult savedSubjectResult() {
        return MentorSubjectResult.builder()
                .id(UuidUtil.generateUuidV7())
                .mentorProfile(profile)
                .subjectCode("PRJ301")
                .subjectName("Java Web")
                .scoreValue(BigDecimal.valueOf(8.5))
                .displayOrder(0)
                .build();
    }
}

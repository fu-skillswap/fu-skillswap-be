package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileBasicRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileExpertiseRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorProfileServiceTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private MentorTagRepository mentorTagRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MentorProfileService mentorProfileService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("mentor@fpt.edu.vn")
                .fullName("Mentor User")
                .avatarUrl("https://example.com/old-avatar.jpg")
                .build();
    }

    @Test
    void getMyProfile_notCreated_shouldReturnEmptyState() {
        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.empty());

        MentorProfileResponse response = mentorProfileService.getMyProfile(userId);

        assertThat(response.exists()).isFalse();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.expertiseTags()).isEmpty();
        assertThat(response.helpTopics()).isEmpty();
    }

    @Test
    void upsertBasic_newProfile_shouldCreateAndUpdateAvatar() {
        MentorProfileBasicRequest request = new MentorProfileBasicRequest(
                " Backend Developer ",
                " Software Engineer ",
                " FPT Software ",
                " https://example.com/avatar.jpg ",
                " Bio ",
                true
        );

        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorTagRepository.findByIdMentorUserIdAndIdTagTypeIn(eq(userId), anyCollection())).thenReturn(List.of());

        MentorProfileResponse response = mentorProfileService.upsertBasic(userId, request);

        ArgumentCaptor<MentorProfile> profileCaptor = ArgumentCaptor.forClass(MentorProfile.class);
        verify(mentorProfileRepository).save(profileCaptor.capture());
        MentorProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.getHeadline()).isEqualTo("Backend Developer");
        assertThat(savedProfile.getCurrentPosition()).isEqualTo("Software Engineer");
        assertThat(savedProfile.getCurrentCompany()).isEqualTo("FPT Software");
        assertThat(savedProfile.getBio()).isEqualTo("Bio");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/avatar.jpg");
        assertThat(response.exists()).isTrue();
    }

    @Test
    void upsertExpertise_duplicateTags_shouldRejectBeforeWriting() {
        UUID tagId = UUID.randomUUID();
        MentorProfile profile = MentorProfile.builder()
                .userId(userId)
                .user(user)
                .build();
        MentorProfileExpertiseRequest request = new MentorProfileExpertiseRequest(
                List.of(tagId, tagId),
                List.of(UUID.randomUUID()),
                BigDecimal.ONE,
                "Software Engineering",
                null,
                null,
                null,
                null
        );

        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> mentorProfileService.upsertExpertise(userId, request))
                .isInstanceOf(BaseException.class)
                .hasMessage("Danh sách tag chuyên môn không được trùng lặp");
    }

    @Test
    void upsertExpertise_validTags_shouldReplaceBothTagGroupsInBatch() {
        UUID expertiseTagId = UUID.randomUUID();
        UUID helpTopicId = UUID.randomUUID();
        Tag expertiseTag = Tag.builder()
                .id(expertiseTagId)
                .code("SPRING_BOOT")
                .nameVi("Spring Boot")
                .type(TagType.TECH_SKILL)
                .status(TagStatus.ACTIVE)
                .build();
        Tag helpTopic = Tag.builder()
                .id(helpTopicId)
                .code("CV_REVIEW")
                .nameVi("CV Review")
                .type(TagType.HELP_TOPIC)
                .status(TagStatus.ACTIVE)
                .build();
        MentorProfile profile = MentorProfile.builder()
                .userId(userId)
                .user(user)
                .build();
        MentorProfileExpertiseRequest request = new MentorProfileExpertiseRequest(
                List.of(expertiseTagId),
                List.of(helpTopicId),
                new BigDecimal("2.5"),
                "Software Engineering",
                "Backend APIs",
                null,
                null,
                null
        );

        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.of(profile));
        when(tagRepository.findByIdInAndStatus(anyCollection(), eq(TagStatus.ACTIVE)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Collection<UUID> ids = invocation.getArgument(0);
                    if (ids.contains(expertiseTagId)) {
                        return List.of(expertiseTag);
                    }
                    return List.of(helpTopic);
                });
        when(mentorProfileRepository.save(profile)).thenReturn(profile);
        when(mentorTagRepository.findByIdMentorUserIdAndIdTagTypeIn(eq(userId), anyCollection()))
                .thenReturn(List.of(
                        MentorTag.builder().mentorProfile(profile).tag(expertiseTag)
                                .id(new com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId(userId, expertiseTagId, MentorTagType.EXPERTISE))
                                .build(),
                        MentorTag.builder().mentorProfile(profile).tag(helpTopic)
                                .id(new com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId(userId, helpTopicId, MentorTagType.HELP_TOPIC))
                                .build()
                ));

        MentorProfileResponse response = mentorProfileService.upsertExpertise(userId, request);

        verify(mentorTagRepository).deleteByIdMentorUserIdAndIdTagType(userId, MentorTagType.EXPERTISE);
        verify(mentorTagRepository).deleteByIdMentorUserIdAndIdTagType(userId, MentorTagType.HELP_TOPIC);
        verify(mentorTagRepository, times(2)).saveAll(anyCollection());
        assertThat(response.expertiseTags()).hasSize(1);
        assertThat(response.helpTopics()).hasSize(1);
        assertThat(response.industry()).isEqualTo("Software Engineering");
    }
}

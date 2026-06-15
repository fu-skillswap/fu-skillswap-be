package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileUpsertRequest;
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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
        assertThat(response.helpTopics()).isEmpty();
    }

    @Test
    void upsertProfile_newProfile_shouldSaveProfileWithoutUpdatingAvatar() {
        UUID helpTopicId = UUID.randomUUID();
        Tag helpTopic = activeTag(helpTopicId, "CV_REVIEW", TagType.HELP_TOPIC);
        MentorProfileUpsertRequest request = validRequest(List.of(helpTopicId));

        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tagRepository.findByIdInAndStatus(anyCollection(), eq(TagStatus.ACTIVE)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Collection<UUID> ids = invocation.getArgument(0);
                    return List.of(helpTopic);
                });
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(invocation -> {
            MentorProfile profile = invocation.getArgument(0);
            if (profile.getUserId() == null && profile.getUser() != null) {
                profile.setUserId(profile.getUser().getId());
            }
            return profile;
        });

        MentorProfileResponse response = mentorProfileService.upsertProfile(userId, request);

        ArgumentCaptor<MentorProfile> profileCaptor = ArgumentCaptor.forClass(MentorProfile.class);
        verify(mentorProfileRepository).save(profileCaptor.capture());
        MentorProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.getHeadline()).isEqualTo("Backend Developer");
        assertThat(savedProfile.getExpertiseDescription()).isEqualTo("Có kinh nghiệm Spring Boot và PostgreSQL");
        assertThat(savedProfile.getTeachingMode()).isEqualTo(TeachingMode.ONLINE);
        assertThat(savedProfile.getSessionDuration()).isEqualTo(60);
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/old-avatar.jpg");
        assertThat(response.exists()).isTrue();
    }

    @Test
    void upsertProfile_duplicateHelpTopics_shouldRejectBeforeWriting() {
        UUID tagId = UUID.randomUUID();
        MentorProfile profile = MentorProfile.builder()
                .userId(userId)
                .user(user)
                .build();
        MentorProfileUpsertRequest request = validRequest(List.of(tagId, tagId));

        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> mentorProfileService.upsertProfile(userId, request))
                .isInstanceOf(BaseException.class)
                .hasMessage("Danh sách chủ đề hỗ trợ không được trùng lặp");
    }

    @Test
    void upsertProfile_validHelpTopics_shouldReplaceTagGroupInBatch() {
        UUID helpTopicId = UUID.randomUUID();
        Tag helpTopic = activeTag(helpTopicId, "CV_REVIEW", TagType.HELP_TOPIC);
        MentorProfile profile = MentorProfile.builder()
                .userId(userId)
                .user(user)
                .build();
        MentorProfileUpsertRequest request = validRequest(List.of(helpTopicId));

        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.of(profile));
        when(tagRepository.findByIdInAndStatus(anyCollection(), eq(TagStatus.ACTIVE)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Collection<UUID> ids = invocation.getArgument(0);
                    return List.of(helpTopic);
                });
        when(mentorProfileRepository.save(profile)).thenReturn(profile);

        MentorProfileResponse response = mentorProfileService.upsertProfile(userId, request);

        verify(mentorTagRepository).deleteByIdMentorUserId(userId);
        verify(mentorTagRepository, times(1)).saveAll(anyCollection());
        assertThat(response.helpTopics()).hasSize(1);
        assertThat(response.teachingMode()).isEqualTo(TeachingMode.ONLINE);
    }

    private MentorProfileUpsertRequest validRequest(List<UUID> helpTopicIds) {
        return new MentorProfileUpsertRequest(
                " Backend Developer ",
                " Có kinh nghiệm Spring Boot và PostgreSQL ",
                "Cơ sở dữ liệu, Lập trình Java, Kiến trúc API",
                true,
                helpTopicIds,
                TeachingMode.ONLINE,
                60,
                null,
                null,
                null
        );
    }

    private Tag activeTag(UUID id, String code, TagType type) {
        return Tag.builder()
                .id(id)
                .code(code)
                .nameVi(code)
                .type(type)
                .status(TagStatus.ACTIVE)
                .build();
    }
}

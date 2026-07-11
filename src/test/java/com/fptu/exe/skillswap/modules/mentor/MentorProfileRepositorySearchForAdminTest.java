package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorListItemResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
public class MentorProfileRepositorySearchForAdminTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    private User createTestUser(String email, UserStatus status) {
        User user = User.builder()
                .email(email)
                .fullName("Test " + email)
                .status(status)
                .roles(Set.of(RoleCode.MENTOR))
                .build();
        return entityManager.persist(user);
    }

    private MentorProfile createProfile(User user, MentorStatus status) {
        MentorProfile profile = MentorProfile.builder()
                .user(user)
                .status(status)
                .headline("Headline of " + user.getEmail())
                .expertiseDescription("Test expertise")
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .isAvailable(true)
                .build();
        return entityManager.persist(profile);
    }

    @Test
    void searchForAdmin_whenStatusIsNull_shouldExcludeDraftProfiles() {
        User user1 = createTestUser("active@test.com", UserStatus.ACTIVE);
        createProfile(user1, MentorStatus.ACTIVE);

        User user2 = createTestUser("draft@test.com", UserStatus.ACTIVE);
        createProfile(user2, MentorStatus.DRAFT);

        User user3 = createTestUser("pending@test.com", UserStatus.ACTIVE);
        createProfile(user3, MentorStatus.PENDING_VERIFICATION);

        entityManager.flush();

        Page<AdminMentorListItemResponse> result = mentorProfileRepository.searchForAdmin(
                null, null, "", "", null, null, PageRequest.of(0, 10)
        );

        List<AdminMentorListItemResponse> content = result.getContent();
        assertEquals(2, content.size());
        assertTrue(content.stream().noneMatch(mp -> mp.mentorStatus() == MentorStatus.DRAFT));
        assertTrue(content.stream().anyMatch(mp -> mp.mentorStatus() == MentorStatus.ACTIVE));
        assertTrue(content.stream().anyMatch(mp -> mp.mentorStatus() == MentorStatus.PENDING_VERIFICATION));
    }

    @Test
    void searchForAdmin_whenStatusIsDraft_shouldReturnOnlyDraftProfiles() {
        User user1 = createTestUser("active@test.com", UserStatus.ACTIVE);
        createProfile(user1, MentorStatus.ACTIVE);

        User user2 = createTestUser("draft@test.com", UserStatus.ACTIVE);
        createProfile(user2, MentorStatus.DRAFT);

        User user3 = createTestUser("pending@test.com", UserStatus.ACTIVE);
        createProfile(user3, MentorStatus.PENDING_VERIFICATION);

        entityManager.flush();

        Page<AdminMentorListItemResponse> result = mentorProfileRepository.searchForAdmin(
                null, null, "", "", MentorStatus.DRAFT, null, PageRequest.of(0, 10)
        );

        List<AdminMentorListItemResponse> content = result.getContent();
        assertEquals(1, content.size());
        assertEquals(MentorStatus.DRAFT, content.get(0).mentorStatus());
        assertEquals("Test draft@test.com", content.get(0).displayName());
    }

    @Test
    void searchForAdmin_whenStatusIsActive_shouldReturnOnlyActiveProfiles() {
        User user1 = createTestUser("active@test.com", UserStatus.ACTIVE);
        createProfile(user1, MentorStatus.ACTIVE);

        User user2 = createTestUser("draft@test.com", UserStatus.ACTIVE);
        createProfile(user2, MentorStatus.DRAFT);

        entityManager.flush();

        Page<AdminMentorListItemResponse> result = mentorProfileRepository.searchForAdmin(
                null, null, "", "", MentorStatus.ACTIVE, null, PageRequest.of(0, 10)
        );

        List<AdminMentorListItemResponse> content = result.getContent();
        assertEquals(1, content.size());
        assertEquals(MentorStatus.ACTIVE, content.get(0).mentorStatus());
        assertEquals("Test active@test.com", content.get(0).displayName());
    }
}

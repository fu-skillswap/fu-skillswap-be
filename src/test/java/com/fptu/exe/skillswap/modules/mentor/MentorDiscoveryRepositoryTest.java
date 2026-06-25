package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
public class MentorDiscoveryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    private User createTestUser(String email, Set<RoleCode> roles, UserStatus status) {
        User user = User.builder()
                .email(email)
                .fullName("Test " + email)
                .status(status)
                .roles(roles)
                .build();
        return entityManager.persist(user);
    }

    private MentorProfile createDiscoverableProfile(User user, String headline) {
        return createDiscoverableProfile(user, headline, "PRJ301, SWP391");
    }

    private MentorProfile createDiscoverableProfile(User user, String headline, String supportingSubjects) {
        StudentProfile sp = StudentProfile.builder()
                .user(user)
                .claimedStudentCode("SC" + UUID.randomUUID().toString().substring(0, 5))
                .semester(5)
                .intakeYear(2022)
                .build();
        entityManager.persist(sp);

        MentorProfile profile = MentorProfile.builder()
                .user(user)
                .status(MentorStatus.ACTIVE)
                .headline(headline)
                .expertiseDescription("Test expertise")
                .supportingSubjects(supportingSubjects)
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .isAvailable(true)
                .verifiedAt(LocalDateTime.now().minusDays(1))
                .build();
        MentorProfile savedProfile = entityManager.persist(profile);

        com.fptu.exe.skillswap.modules.catalog.domain.Tag tag = com.fptu.exe.skillswap.modules.catalog.domain.Tag.builder()
                .code("T" + UUID.randomUUID().toString().substring(0, 5))
                .nameVi("Test Tag")
                .type(com.fptu.exe.skillswap.modules.catalog.domain.TagType.HELP_TOPIC)
                .status(com.fptu.exe.skillswap.modules.catalog.domain.TagStatus.ACTIVE)
                .build();
        entityManager.persist(tag);

        com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mentorTag = com.fptu.exe.skillswap.modules.catalog.domain.MentorTag.builder()
                .id(new com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId(user.getId(), tag.getId(), MentorTagType.HELP_TOPIC))
                .mentorProfile(savedProfile)
                .tag(tag)
                .build();
        entityManager.persist(mentorTag);

        return savedProfile;
    }

    @Test
    void searchMentors_shouldNotReturnMenteeOnlyUser() {
        User user = createTestUser("mentee@test.com", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE);
        createDiscoverableProfile(user, "Mentee Headline");
        entityManager.flush();

        Page<UUID> result = mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                MentorStatus.ACTIVE, MentorTagType.HELP_TOPIC, null, null, null, false, List.of(),
                null, null, "", "", LocalDateTime.now(), PageRequest.of(0, 10)
        );
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void searchMentors_shouldReturnApprovedMentorWithMenteeAndMentorRoles() {
        User user = createTestUser("mentor@test.com", Set.of(RoleCode.MENTEE, RoleCode.MENTOR), UserStatus.ACTIVE);
        createDiscoverableProfile(user, "Mentor Headline");
        entityManager.flush();

        Page<UUID> result = mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                MentorStatus.ACTIVE, MentorTagType.HELP_TOPIC, null, null, null, false, List.of(),
                null, null, "", "", LocalDateTime.now(), PageRequest.of(0, 10)
        );
        assertEquals(1, result.getContent().size());
        assertEquals(user.getId(), result.getContent().get(0));
    }

    @Test
    void searchMentors_shouldNotReturnAdmin() {
        User user = createTestUser("admin@test.com", Set.of(RoleCode.ADMIN, RoleCode.MENTOR), UserStatus.ACTIVE);
        createDiscoverableProfile(user, "Admin Headline");
        entityManager.flush();

        Page<UUID> result = mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                MentorStatus.ACTIVE, MentorTagType.HELP_TOPIC, null, null, null, false, List.of(),
                null, null, "", "", LocalDateTime.now(), PageRequest.of(0, 10)
        );
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void searchMentors_shouldNotReturnSystemAdmin() {
        User user = createTestUser("sysadmin@test.com", Set.of(RoleCode.SYSTEM_ADMIN, RoleCode.MENTOR), UserStatus.ACTIVE);
        createDiscoverableProfile(user, "Sysadmin Headline");
        entityManager.flush();

        Page<UUID> result = mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                MentorStatus.ACTIVE, MentorTagType.HELP_TOPIC, null, null, null, false, List.of(),
                null, null, "", "", LocalDateTime.now(), PageRequest.of(0, 10)
        );
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void searchMentors_shouldNotReturnInactiveOrBannedUser() {
        User user = createTestUser("banned@test.com", Set.of(RoleCode.MENTEE, RoleCode.MENTOR), UserStatus.BANNED);
        createDiscoverableProfile(user, "Banned Headline");
        entityManager.flush();

        Page<UUID> result = mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                MentorStatus.ACTIVE, MentorTagType.HELP_TOPIC, null, null, null, false, List.of(),
                null, null, "", "", LocalDateTime.now(), PageRequest.of(0, 10)
        );
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void searchMentors_withoutKeyword_shouldNotReturnMenteeOnlyUser() {
        User user = createTestUser("mentee-nokey@test.com", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE);
        createDiscoverableProfile(user, "Mentee Headline");
        entityManager.flush();

        Page<UUID> result = mentorProfileRepository.findDiscoverableCandidateIds(
                MentorStatus.ACTIVE, MentorTagType.HELP_TOPIC, null, null, null, false, List.of(),
                LocalDateTime.now(), PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void searchMentors_withoutKeyword_shouldNotReturnAdmin() {
        User user = createTestUser("admin-nokey@test.com", Set.of(RoleCode.ADMIN, RoleCode.MENTOR), UserStatus.ACTIVE);
        createDiscoverableProfile(user, "Admin Headline");
        entityManager.flush();

        Page<UUID> result = mentorProfileRepository.findDiscoverableCandidateIds(
                MentorStatus.ACTIVE, MentorTagType.HELP_TOPIC, null, null, null, false, List.of(),
                LocalDateTime.now(), PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void searchMentors_keywordShouldMatchVietnameseTextWithoutDiacritics() {
        User user = createTestUser("accented@test.com", Set.of(RoleCode.MENTEE, RoleCode.MENTOR), UserStatus.ACTIVE);
        createDiscoverableProfile(user, "Hướng dẫn môn học");
        entityManager.flush();

        Page<UUID> result = mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                MentorStatus.ACTIVE, MentorTagType.HELP_TOPIC, null, null, null, false, List.of(),
                "%huong dan mon hoc%", "%huong dan mon hoc%",
                "àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ",
                "aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy",
                LocalDateTime.now(), PageRequest.of(0, 10)
        );

        assertEquals(1, result.getContent().size());
        assertEquals(user.getId(), result.getContent().getFirst());
    }

    @Test
    void searchMentors_keywordShouldMatchSupportingSubjectsAndServiceExpectedOutcome() {
        User user = createTestUser("service@test.com", Set.of(RoleCode.MENTEE, RoleCode.MENTOR), UserStatus.ACTIVE);
        MentorProfile profile = createDiscoverableProfile(user, "Mentor hỗ trợ học tập", "SWP391, Lập trình web");

        com.fptu.exe.skillswap.modules.mentor.domain.MentorService mentorService = com.fptu.exe.skillswap.modules.mentor.domain.MentorService.builder()
                .mentorProfile(profile)
                .title("Hướng dẫn Spring Boot")
                .description("Giải thích Spring Boot")
                .expectedOutcome("Hỗ trợ SWP391")
                .durationMinutes(60)
                .isFree(true)
                .build();
        entityManager.persist(mentorService);
        entityManager.flush();

        Page<UUID> result = mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                MentorStatus.ACTIVE, MentorTagType.HELP_TOPIC, null, null, null, false, List.of(),
                "%swp391%", "%swp391%",
                "àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ",
                "aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy",
                LocalDateTime.now(), PageRequest.of(0, 10)
        );

        assertEquals(1, result.getContent().size());
        assertEquals(user.getId(), result.getContent().getFirst());
    }
}

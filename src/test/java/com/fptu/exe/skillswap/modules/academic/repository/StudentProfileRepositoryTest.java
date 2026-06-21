package com.fptu.exe.skillswap.modules.academic.repository;

import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class StudentProfileRepositoryTest {

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void migration_shouldAllowDuplicateClaimedStudentCode() {
        User user1 = createUser("user1@test.com");
        User user2 = createUser("user2@test.com");

        StudentProfile profile1 = new StudentProfile();
        profile1.setUser(user1);
        profile1.setClaimedStudentCode("SE123456");

        StudentProfile profile2 = new StudentProfile();
        profile2.setUser(user2);
        profile2.setClaimedStudentCode("SE123456"); // Same claimed code

        assertDoesNotThrow(() -> {
            studentProfileRepository.saveAndFlush(profile1);
            studentProfileRepository.saveAndFlush(profile2);
        });

        assertEquals(2, studentProfileRepository.count());
    }

    @Test
    void migration_shouldAllowDuplicateClaimedStudentCodeAcrossMultipleProfiles() {
        User user1 = createUser("user3@test.com");
        User user2 = createUser("user4@test.com");

        StudentProfile profile1 = new StudentProfile();
        profile1.setUser(user1);
        profile1.setClaimedStudentCode("SE111");

        StudentProfile profile2 = new StudentProfile();
        profile2.setUser(user2);
        profile2.setClaimedStudentCode("SE111");

        assertDoesNotThrow(() -> {
            studentProfileRepository.saveAndFlush(profile1);
            studentProfileRepository.saveAndFlush(profile2);
        });
    }

    private User createUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFullName("Test User");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }
}

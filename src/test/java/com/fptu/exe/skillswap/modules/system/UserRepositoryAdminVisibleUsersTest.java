package com.fptu.exe.skillswap.modules.system;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UserRepositoryAdminVisibleUsersTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void searchAdminVisibleUsers_shouldReturnOnlyMenteeAndMentorUsers() {
        User mentee = saveUser("mentee@test.com", "Mentee User", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE);
        User mentor = saveUser("mentor@test.com", "Mentor User", Set.of(RoleCode.MENTOR), UserStatus.ACTIVE);
        saveUser("admin-mentee@test.com", "Admin Mentee", Set.of(RoleCode.MENTEE, RoleCode.SYSTEM_ADMIN), UserStatus.ACTIVE);
        saveUser("admin@test.com", "Admin User", Set.of(RoleCode.ADMIN), UserStatus.ACTIVE);

        Page<User> page = userRepository.searchAdminVisibleUsers(
                null,
                null,
                null,
                RoleCode.MENTEE,
                RoleCode.MENTOR,
                RoleCode.ADMIN,
                RoleCode.SYSTEM_ADMIN,
                PageRequest.of(0, 20)
        );

        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().anyMatch(user -> user.getId().equals(mentee.getId())));
        assertTrue(page.getContent().stream().anyMatch(user -> user.getId().equals(mentor.getId())));
    }

    @Test
    void searchAdminVisibleUsers_withRoleFilterAndKeyword_shouldFilterWithoutDuplicates() {
        saveUser("mentor.spring@test.com", "Nguyen Van Spring", Set.of(RoleCode.MENTEE, RoleCode.MENTOR), UserStatus.ACTIVE);
        saveUser("mentee.react@test.com", "Tran React", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE);

        Page<User> page = userRepository.searchAdminVisibleUsers(
                "%spring%",
                UserStatus.ACTIVE,
                RoleCode.MENTOR,
                RoleCode.MENTEE,
                RoleCode.MENTOR,
                RoleCode.ADMIN,
                RoleCode.SYSTEM_ADMIN,
                PageRequest.of(0, 20)
        );

        assertEquals(1, page.getTotalElements());
        assertEquals("mentor.spring@test.com", page.getContent().getFirst().getEmail());
    }

    @Test
    void searchAdminVisibleUsers_roleAndStatusFilteringEndToEnd() {
        // Setup User A, B, C, D, E, F
        User userA = saveUser("a@test.com", "User A", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE);
        User userB = saveUser("b@test.com", "User B", Set.of(RoleCode.MENTEE, RoleCode.MENTOR), UserStatus.ACTIVE);
        User userC = saveUser("c@test.com", "User C", Set.of(RoleCode.MENTOR), UserStatus.ACTIVE);
        User userD = saveUser("d@test.com", "User D", Set.of(RoleCode.MENTEE), UserStatus.INACTIVE);
        User userE = saveUser("e@test.com", "User E", Set.of(RoleCode.ADMIN), UserStatus.ACTIVE);
        User userF = saveUser("f@test.com", "User F", Set.of(RoleCode.SYSTEM_ADMIN), UserStatus.ACTIVE);

        // 1. role=MENTEE & status=ACTIVE -> returns only User A
        Page<User> page1 = userRepository.searchAdminVisibleUsers(
                null,
                UserStatus.ACTIVE,
                RoleCode.MENTEE,
                RoleCode.MENTEE,
                RoleCode.MENTOR,
                RoleCode.ADMIN,
                RoleCode.SYSTEM_ADMIN,
                PageRequest.of(0, 20)
        );
        assertEquals(1, page1.getTotalElements());
        assertEquals(userA.getId(), page1.getContent().getFirst().getId());

        // 2. role=MENTOR & status=ACTIVE -> returns User B and User C
        Page<User> page2 = userRepository.searchAdminVisibleUsers(
                null,
                UserStatus.ACTIVE,
                RoleCode.MENTOR,
                RoleCode.MENTEE,
                RoleCode.MENTOR,
                RoleCode.ADMIN,
                RoleCode.SYSTEM_ADMIN,
                PageRequest.of(0, 20)
        );
        assertEquals(2, page2.getTotalElements());
        Set<String> emails2 = page2.getContent().stream().map(User::getEmail).collect(Collectors.toSet());
        assertTrue(emails2.contains("b@test.com"));
        assertTrue(emails2.contains("c@test.com"));

        // 3. status=ACTIVE without role -> returns User A, User B, and User C only (excludes ADMIN/SYSTEM_ADMIN)
        Page<User> page3 = userRepository.searchAdminVisibleUsers(
                null,
                UserStatus.ACTIVE,
                null,
                RoleCode.MENTEE,
                RoleCode.MENTOR,
                RoleCode.ADMIN,
                RoleCode.SYSTEM_ADMIN,
                PageRequest.of(0, 20)
        );
        assertEquals(3, page3.getTotalElements());
        Set<String> emails3 = page3.getContent().stream().map(User::getEmail).collect(Collectors.toSet());
        assertTrue(emails3.contains("a@test.com"));
        assertTrue(emails3.contains("b@test.com"));
        assertTrue(emails3.contains("c@test.com"));
        assertTrue(!emails3.contains("e@test.com"));
        assertTrue(!emails3.contains("f@test.com"));

        // 4. role=MENTEE without status -> returns User A and User D, but not User B
        Page<User> page4 = userRepository.searchAdminVisibleUsers(
                null,
                null,
                RoleCode.MENTEE,
                RoleCode.MENTEE,
                RoleCode.MENTOR,
                RoleCode.ADMIN,
                RoleCode.SYSTEM_ADMIN,
                PageRequest.of(0, 20)
        );
        assertEquals(2, page4.getTotalElements());
        Set<String> emails4 = page4.getContent().stream().map(User::getEmail).collect(Collectors.toSet());
        assertTrue(emails4.contains("a@test.com"));
        assertTrue(emails4.contains("d@test.com"));
        assertTrue(!emails4.contains("b@test.com"));
    }

    private User saveUser(String email, String fullName, Set<RoleCode> roles, UserStatus status) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setStatus(status);
        user.setRoles(roles);
        return userRepository.save(user);
    }
}

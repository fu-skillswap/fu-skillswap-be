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

    private User saveUser(String email, String fullName, Set<RoleCode> roles, UserStatus status) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setStatus(status);
        user.setRoles(roles);
        return userRepository.save(user);
    }
}

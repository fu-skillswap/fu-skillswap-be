package com.fptu.exe.skillswap.modules.admin.repository;

import com.fptu.exe.skillswap.modules.identity.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.identity.domain.Campus;
import com.fptu.exe.skillswap.modules.identity.domain.CampusCode;
import com.fptu.exe.skillswap.modules.identity.domain.Specialization;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.identity.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.identity.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryAdminVisibleUsersTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private AcademicProgramRepository academicProgramRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    private Campus campus;
    private AcademicProgram program;
    private Specialization specialization;

    @BeforeEach
    void setUp() {
        studentProfileRepository.deleteAllInBatch();
        userRepository.deleteAll();
        campus = campusRepository.findByCode(CampusCode.HCM)
                .orElseGet(() -> campusRepository.save(Campus.builder()
                        .code(CampusCode.HCM)
                        .name("TP. Hồ Chí Minh")
                        .city("Hồ Chí Minh")
                        .isActive(true)
                        .build()));
        program = academicProgramRepository.findByCode("CNTT")
                .orElseGet(() -> academicProgramRepository.save(AcademicProgram.builder()
                        .code("CNTT")
                        .nameVi("Công nghệ thông tin")
                        .nameEn("Information Technology")
                        .isActive(true)
                        .build()));
        specialization = specializationRepository.findByCode("CNTT_TTNT")
                .orElseGet(() -> specializationRepository.save(Specialization.builder()
                        .program(program)
                        .code("CNTT_TTNT")
                        .nameVi("Trí tuệ nhân tạo")
                        .nameEn("Artificial Intelligence")
                        .isActive(true)
                        .build()));
    }

    @Test
    void searchAdminVisibleUsers_shouldReturnOnlyMenteeAndMentorUsersWithCompletedStudentProfile() {
        User mentee = saveUserWithStudentProfile("mentee@test.com", "Mentee User", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE, "SE180001");
        User mentor = saveUserWithStudentProfile("mentor@test.com", "Mentor User", Set.of(RoleCode.MENTOR), UserStatus.ACTIVE, "SE180002");
        saveUserWithStudentProfile("admin-mentee@test.com", "Admin Mentee", Set.of(RoleCode.MENTEE, RoleCode.SYSTEM_ADMIN), UserStatus.ACTIVE, "SE180003");
        saveUserWithStudentProfile("admin@test.com", "Admin User", Set.of(RoleCode.ADMIN), UserStatus.ACTIVE, "SE180004");
        User withoutProfile = saveUser("no-profile@test.com", "No Profile User", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE);
        User incompleteProfile = saveUser("incomplete-profile@test.com", "Incomplete Profile", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE);
        studentProfileRepository.save(StudentProfile.builder()
                .user(incompleteProfile)
                .claimedStudentCode("   ")
                .campus(campus)
                .program(program)
                .specialization(specialization)
                .semester(5)
                .isAlumni(false)
                .build());

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
        assertTrue(page.getContent().stream().noneMatch(user -> user.getId().equals(withoutProfile.getId())));
        assertTrue(page.getContent().stream().noneMatch(user -> user.getId().equals(incompleteProfile.getId())));
    }

    @Test
    void searchAdminVisibleUsers_withRoleFilterAndKeyword_shouldFilterWithoutDuplicates() {
        saveUserWithStudentProfile("mentor.spring@test.com", "Nguyen Van Spring", Set.of(RoleCode.MENTEE, RoleCode.MENTOR), UserStatus.ACTIVE, "SE180010");
        saveUserWithStudentProfile("mentee.react@test.com", "Tran React", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE, "SE180011");

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
        User userA = saveUserWithStudentProfile("a@test.com", "User A", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE, "SE180101");
        User userB = saveUserWithStudentProfile("b@test.com", "User B", Set.of(RoleCode.MENTEE, RoleCode.MENTOR), UserStatus.ACTIVE, "SE180102");
        User userC = saveUserWithStudentProfile("c@test.com", "User C", Set.of(RoleCode.MENTOR), UserStatus.ACTIVE, "SE180103");
        User userD = saveUserWithStudentProfile("d@test.com", "User D", Set.of(RoleCode.MENTEE), UserStatus.INACTIVE, "SE180104");
        User userE = saveUserWithStudentProfile("e@test.com", "User E", Set.of(RoleCode.ADMIN), UserStatus.ACTIVE, "SE180105");
        User userF = saveUserWithStudentProfile("f@test.com", "User F", Set.of(RoleCode.SYSTEM_ADMIN), UserStatus.ACTIVE, "SE180106");

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

    private User saveUserWithStudentProfile(String email, String fullName, Set<RoleCode> roles, UserStatus status, String studentCode) {
        User user = saveUser(email, fullName, roles, status);
        studentProfileRepository.save(StudentProfile.builder()
                .user(user)
                .claimedStudentCode(studentCode)
                .campus(campus)
                .program(program)
                .specialization(specialization)
                .semester(5)
                .intakeYear(2022)
                .isAlumni(false)
                .build());
        return user;
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

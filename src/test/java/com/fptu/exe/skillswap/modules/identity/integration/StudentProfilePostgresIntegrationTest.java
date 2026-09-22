package com.fptu.exe.skillswap.modules.identity.integration;

import com.fptu.exe.skillswap.infrastructure.testcontainer.AbstractPostgreSQLIntegrationTest;
import com.fptu.exe.skillswap.modules.catalog.repository.AdministrativeProvinceRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationFieldGroupRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationalInstitutionRepository;
import com.fptu.exe.skillswap.modules.catalog.service.EducationCatalogService;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfileType;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.identity.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.test.database.replace=none"
})
@Transactional
class StudentProfilePostgresIntegrationTest extends AbstractPostgreSQLIntegrationTest {
    @Autowired private AdministrativeProvinceRepository provinces;
    @Autowired private EducationalInstitutionRepository institutions;
    @Autowired private EducationFieldGroupRepository fields;
    @Autowired private EducationCatalogService catalog;
    @Autowired private AcademicService academicService;
    @Autowired private UserRepository users;
    @Autowired private com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository profiles;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    @Test
    void freshPostgresSchemaSeedsCatalogAndEnforcesProfileRules() {
        assertEquals(34, provinces.count());
        assertEquals(34, provinces.findAll().stream().map(p -> p.getCode()).distinct().count());
        assertTrue(institutions.findAll().stream().allMatch(i -> i.getProvince() != null));
        assertEquals(fields.count(), fields.findAll().stream().map(g -> g.getOfficialCode()).distinct().count());
        assertTrue(catalog.provinces("ha noi").stream().anyMatch(p -> p.name().equals("Hà Nội")));
        assertEquals("734", catalog.fieldGroups("marketing", 0, 10).getContent().getFirst().officialCode());
        try (Connection connection = dataSource.getConnection()) {
            assertLegacySchemaRemoved(connection);
        } catch (java.sql.SQLException e) {
            throw new AssertionError(e);
        }

        User user = users.save(User.builder().email("pg-profile-" + UUID.randomUUID() + "@test.local")
                .fullName("Postgres profile").status(UserStatus.ACTIVE).build());
        var hanoi = provinces.findByCode("01").orElseThrow();
        var university = institutions.findBySlug("hust").orElseThrow();
        var field = fields.findByOfficialCode("748").orElseThrow();

        var school = academicService.updateStudentProfile(user.getId(), StudentProfileRequest.builder()
                .profileType(StudentProfileType.SCHOOL_STUDENT).customInstitutionName("THPT Test")
                .customInstitutionProvinceId(hanoi.getId()).build());
        assertEquals(StudentProfileType.SCHOOL_STUDENT, school.getProfileType());

        assertThrows(BaseException.class, () -> academicService.updateStudentProfile(user.getId(), StudentProfileRequest.builder()
                .profileType(StudentProfileType.UNIVERSITY_STUDENT).institutionId(university.getId())
                .customInstitutionProvinceId(hanoi.getId()).fieldGroupId(field.getId()).majorName("Khoa học máy tính").build()));
        assertThrows(BaseException.class, () -> academicService.updateStudentProfile(user.getId(), StudentProfileRequest.builder()
                .profileType(StudentProfileType.ALUMNI).institutionId(university.getId())
                .fieldGroupId(field.getId()).majorName("Khoa học máy tính").build()));

        var alumni = academicService.updateStudentProfile(user.getId(), StudentProfileRequest.builder()
                .profileType(StudentProfileType.ALUMNI).institutionId(university.getId())
                .fieldGroupId(field.getId()).majorName("Khoa học máy tính").graduationYear(2025).build());
        assertEquals(StudentProfileType.ALUMNI, alumni.getProfileType());
    }

    @Test
    void ownerReadContractSupportsEachCurrentProfileTypeAndDoesNotCrossOwners() {
        var schoolUser = createUser("school-read");
        var schoolProvince = provinces.findByCode("01").orElseThrow();
        var school = academicService.updateStudentProfile(schoolUser.getId(), StudentProfileRequest.builder()
                .profileType(StudentProfileType.SCHOOL_STUDENT).customInstitutionName("THPT Example")
                .customInstitutionProvinceId(schoolProvince.getId()).build());
        assertEquals(StudentProfileType.SCHOOL_STUDENT, school.getProfileType());
        assertEquals("THPT Example", school.getCustomInstitutionName());
        assertEquals(schoolProvince.getId(), school.getCustomInstitutionProvinceId());
        assertNull(school.getInstitutionId());
        assertNull(school.getFieldGroupId());

        var universityUser = createUser("university-read");
        var university = academicService.updateStudentProfile(universityUser.getId(), StudentProfileRequest.builder()
                .profileType(StudentProfileType.UNIVERSITY_STUDENT).institutionId(institutions.findBySlug("hust").orElseThrow().getId())
                .fieldGroupId(fields.findByOfficialCode("748").orElseThrow().getId()).majorName("Computer Science").build());
        assertEquals(StudentProfileType.UNIVERSITY_STUDENT, university.getProfileType());
        assertNotNull(university.getInstitutionId());
        assertNotNull(university.getFieldGroupId());

        var alumniUser = createUser("alumni-read");
        var alumni = academicService.updateStudentProfile(alumniUser.getId(), StudentProfileRequest.builder()
                .profileType(StudentProfileType.ALUMNI).institutionId(institutions.findBySlug("hust").orElseThrow().getId())
                .fieldGroupId(fields.findByOfficialCode("748").orElseThrow().getId())
                .majorName("Computer Science").graduationYear(2024).build());
        assertEquals(StudentProfileType.ALUMNI, alumni.getProfileType());
        assertEquals(2024, alumni.getGraduationYear());

        // Read is keyed by requested owner ID; the response contains no other user's identity data.
        var anotherUser = createUser("other-read");
        academicService.updateStudentProfile(anotherUser.getId(), StudentProfileRequest.builder()
                .profileType(StudentProfileType.SCHOOL_STUDENT).customInstitutionName("Another school")
                .customInstitutionProvinceId(schoolProvince.getId()).build());
        assertEquals(schoolUser.getId(), academicService.getStudentProfile(schoolUser.getId()).getUserId());
        assertNotEquals(anotherUser.getId(), academicService.getStudentProfile(schoolUser.getId()).getUserId());
    }

    private User createUser(String prefix) {
        return users.save(User.builder().email(prefix + "-" + UUID.randomUUID() + "@test.local")
                .fullName(prefix).status(UserStatus.ACTIVE).build());
    }

    // V138_TO_V139 fixture only: upgrade representative pre-contract rows and verify physical removal.
    @Test
    void v138ToV139UpgradeRemovesLegacySchemaAndPreservesCanonicalRows() throws Exception {
        try (PostgreSQLContainer<?> upgrade = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("skillswap_upgrade").withUsername("test").withPassword("test")) {
            upgrade.start();
            Flyway before = Flyway.configure().dataSource(upgrade.getJdbcUrl(), upgrade.getUsername(), upgrade.getPassword())
                    .locations("classpath:db/migration").target("138").load();
            before.migrate();
            UUID userId = UUID.randomUUID();
            UUID canonicalUserId = UUID.randomUUID();
            UUID campusId = UUID.randomUUID();
            UUID programId = UUID.randomUUID();
            UUID specializationId = UUID.randomUUID();
            UUID campaignId = UUID.randomUUID();
            UUID postId = UUID.randomUUID();
            UUID provinceId = UUID.randomUUID();
            UUID institutionId = UUID.randomUUID();
            UUID fieldGroupId = UUID.randomUUID();
            try (Connection connection = java.sql.DriverManager.getConnection(upgrade.getJdbcUrl(), upgrade.getUsername(), upgrade.getPassword());
                 var statement = connection.createStatement()) {
                statement.executeUpdate("insert into users(id,email,full_name,status,created_at,updated_at) values "
                        + "('" + userId + "','legacy-" + userId + "@test.local','Legacy user','ACTIVE',now(),now()),"
                        + "('" + canonicalUserId + "','canonical-" + canonicalUserId + "@test.local','Canonical user','ACTIVE',now(),now())");
                statement.executeUpdate("insert into campuses(id,code,name,is_active) values ('" + campusId + "','HCM','Legacy campus',true)");
                statement.executeUpdate("insert into academic_programs(id,code,name_vi,is_active) values ('" + programId + "','SE','Legacy program',true)");
                statement.executeUpdate("insert into specializations(id,program_id,code,name_vi,is_active,is_expected) values ('" + specializationId + "','" + programId + "','SE1','Legacy specialization',true,false)");
                statement.executeUpdate("insert into student_profiles(id,user_id,student_code,campus_id,program_id,specialization_id,semester,is_alumni,created_at,updated_at) "
                        + "values ('" + userId + "','" + userId + "','SE190123','" + campusId + "','" + programId + "','" + specializationId + "',5,false,now(),now())");
                statement.executeUpdate("insert into campaigns(id,name,status,funding_source,budget_scoin,created_at,updated_at) values ('" + campaignId + "','Legacy audience','DRAFT','APP_FUNDED',0,now(),now())");
                statement.executeUpdate("insert into campaign_audience_campus_ids(campaign_id,campus_id) values ('" + campaignId + "','" + campusId + "')");
                statement.executeUpdate("insert into campaign_audience_program_ids(campaign_id,program_id) values ('" + campaignId + "','" + programId + "')");
                statement.executeUpdate("insert into campaign_audience_specialization_ids(campaign_id,specialization_id) values ('" + campaignId + "','" + specializationId + "')");
                statement.executeUpdate("insert into forum_posts(id,author_user_id,forum_topic_id,author_program_id,title,content,status,last_activity_at,created_at,updated_at) "
                        + "values ('" + postId + "','" + userId + "','00000000-0000-7000-8000-000000000001','" + programId + "','Legacy snapshot','Upgrade fixture','PUBLISHED',now(),now(),now())");

                statement.executeUpdate("insert into administrative_provinces(id,code,name,normalized_name,unit_type,sort_order,effective_from,created_at,updated_at) "
                        + "values ('" + provinceId + "','99','Prompt Province','prompt province','PROVINCE',999,current_date,now(),now())");
                statement.executeUpdate("insert into educational_institutions(id,slug,name,province_id,institution_type,normalized_name,sort_order,created_at,updated_at) "
                        + "values ('" + institutionId + "','prompt-institution','Prompt Institution','" + provinceId + "','UNIVERSITY','prompt institution',999,now(),now())");
                statement.executeUpdate("insert into education_field_groups(id,official_code,name,normalized_name,sort_order,created_at,updated_at) "
                        + "values ('" + fieldGroupId + "','99999','Prompt Field','prompt field',999,now(),now())");
                statement.executeUpdate("insert into student_profiles(id,user_id,is_alumni,profile_type,institution_id,field_group_id,major_name,onboarding_completed,version,created_at,updated_at) "
                        + "values ('" + canonicalUserId + "','" + canonicalUserId + "',false,'UNIVERSITY_STUDENT','" + institutionId + "','" + fieldGroupId + "','Canonical major',true,4,now(),now())");
            }
            try (Connection connection = java.sql.DriverManager.getConnection(upgrade.getJdbcUrl(), upgrade.getUsername(), upgrade.getPassword());
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("select id, student_code, profile_type, onboarding_completed, campus_id, program_id, specialization_id from student_profiles where user_id = '" + userId + "'")) {
                assertTrue(result.next());
                assertEquals(userId, result.getObject("id", UUID.class));
                assertEquals("SE190123", result.getString("student_code"));
                assertNull(result.getString("profile_type"));
                assertFalse(result.getBoolean("onboarding_completed"));
                assertTrue(connection.getMetaData().getTables(null, "public", "campuses", null).next());
                assertTrue(connection.getMetaData().getTables(null, "public", "campaign_audience_program_ids", null).next());
                assertTrue(connection.getMetaData().getColumns(null, "public", "forum_posts", "author_program_id").next());
                assertTrue(functionExists(connection, "refresh_mentor_profile_search_index"));
                assertTrue(indexExists(connection, "idx_mentor_profiles_search_vector"));
            }

            Flyway after = Flyway.configure().dataSource(upgrade.getJdbcUrl(), upgrade.getUsername(), upgrade.getPassword())
                    .locations("classpath:db/migration").load();
            after.migrate();
            try (Connection connection = java.sql.DriverManager.getConnection(upgrade.getJdbcUrl(), upgrade.getUsername(), upgrade.getPassword());
                 var statement = connection.createStatement()) {
                assertLegacySchemaRemoved(connection);
                try (var canonical = statement.executeQuery("select sp.profile_type,sp.institution_id,sp.field_group_id,sp.major_name,sp.version,i.name,p.name "
                        + "from student_profiles sp join educational_institutions i on i.id=sp.institution_id "
                        + "join administrative_provinces p on p.id=i.province_id where sp.user_id='" + canonicalUserId + "'")) {
                    assertTrue(canonical.next());
                    assertEquals("UNIVERSITY_STUDENT", canonical.getString(1));
                    assertEquals(institutionId, canonical.getObject(2, UUID.class));
                    assertEquals(fieldGroupId, canonical.getObject(3, UUID.class));
                    assertEquals("Canonical major", canonical.getString(4));
                    assertEquals(4L, canonical.getLong(5));
                    assertEquals("Prompt Institution", canonical.getString(6));
                    assertEquals("Prompt Province", canonical.getString(7));
                }
                assertEquals(1, scalar(connection, "select count(*) from education_field_groups where id='" + fieldGroupId + "'"));
                assertEquals(1, scalar(connection, "select count(*) from student_profiles where user_id='" + userId + "' and profile_type is null"));
            }
        }
    }

    private static void assertLegacySchemaRemoved(Connection connection) {
        try {
            for (String table : new String[]{"campuses", "academic_programs", "specializations", "specialization_tags",
                    "campaign_audience_campus_ids", "campaign_audience_program_ids", "campaign_audience_specialization_ids"}) {
                assertFalse(connection.getMetaData().getTables(null, "public", table, new String[]{"TABLE"}).next(), table + " remains");
            }
            for (String[] column : new String[][]{{"student_profiles", "campus_id"}, {"student_profiles", "program_id"},
                    {"student_profiles", "specialization_id"}, {"student_profiles", "semester"}, {"student_profiles", "intake_year"},
                    {"student_profiles", "is_alumni"}, {"student_profiles", "student_code"}, {"forum_posts", "author_program_id"},
                    {"mentor_profiles", "search_document"}, {"mentor_profiles", "search_vector"}}) {
                assertFalse(connection.getMetaData().getColumns(null, "public", column[0], column[1]).next(), column[0] + "." + column[1] + " remains");
            }
            assertFalse(indexExists(connection, "idx_mentor_profiles_search_vector"));
            for (String function : new String[]{"skillswap_normalize_search_text", "refresh_mentor_profile_search_index",
                    "refresh_mentor_profiles_by_tag", "mentor_profiles_fts_update", "trg_refresh_mentor_profile_search_from_profile",
                    "trg_refresh_mentor_profile_search_from_user", "trg_refresh_mentor_profile_search_from_student_profile",
                    "trg_refresh_mentor_profile_search_from_mentor_tag", "trg_refresh_mentor_profile_search_from_service",
                    "trg_refresh_mentor_profile_search_from_service_tag", "trg_refresh_mentor_profile_search_from_tag",
                    "trg_refresh_mentor_profile_search_from_subject_result"}) {
                assertFalse(functionExists(connection, function), function + " remains");
            }
            try (var statement = connection.createStatement(); var triggers = statement.executeQuery(
                    "select count(*) from pg_trigger where not tgisinternal and tgname like '%search_refresh' or not tgisinternal and tgname='trg_mentor_profiles_fts'")) {
                assertTrue(triggers.next());
                assertEquals(0, triggers.getInt(1));
            }
        } catch (java.sql.SQLException e) {
            throw new AssertionError("Could not inspect PostgreSQL legacy schema", e);
        }
    }

    private static boolean functionExists(Connection connection, String name) throws java.sql.SQLException {
        try (var statement = connection.prepareStatement("select exists(select 1 from pg_proc where proname=?)")) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) { result.next(); return result.getBoolean(1); }
        }
    }

    private static boolean indexExists(Connection connection, String name) throws java.sql.SQLException {
        try (var statement = connection.prepareStatement("select exists(select 1 from pg_indexes where schemaname='public' and indexname=?)")) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) { result.next(); return result.getBoolean(1); }
        }
    }

    private static int scalar(Connection connection, String sql) throws java.sql.SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) { result.next(); return result.getInt(1); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleConcurrentProfileTransactionCannotOverwriteCommittedUpdate() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID userId = tx.execute(status -> {
            User user = users.save(User.builder().email("pg-lock-" + UUID.randomUUID() + "@test.local")
                    .fullName("Locking test").status(UserStatus.ACTIVE).build());
            users.flush();
            profiles.save(StudentProfile.builder().user(user).bio("initial").build());
            return user.getId();
        });

        CountDownLatch secondRead = new CountDownLatch(1);
        CountDownLatch winnerCommitted = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var winner = pool.submit(() -> tx.execute(status -> {
                var profile = profiles.findById(userId).orElseThrow();
                await(secondRead);
                profile.setBio("winner");
                entityManager.flush();
                return profile.getVersion();
            }));
            var stale = pool.submit(() -> tx.execute(status -> {
                var profile = profiles.findById(userId).orElseThrow();
                secondRead.countDown();
                await(winnerCommitted);
                profile.setBio("stale overwrite");
                entityManager.flush();
                return profile.getVersion();
            }));
            long committedVersion = winner.get(20, TimeUnit.SECONDS);
            winnerCommitted.countDown();
            var conflict = assertThrows(java.util.concurrent.ExecutionException.class, () -> stale.get(20, TimeUnit.SECONDS));
            assertTrue(hasCause(conflict, org.springframework.dao.OptimisticLockingFailureException.class)
                            || hasCause(conflict, jakarta.persistence.OptimisticLockException.class),
                    "The stale transaction must fail specifically through JPA optimistic locking");
            assertEquals("winner", tx.execute(status -> profiles.findById(userId).orElseThrow().getBio()));
            assertEquals(1L, committedVersion);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for concurrent transaction");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new AssertionError(e);
        }
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> expected) {
        for (Throwable current = failure; current != null; current = current.getCause())
            if (expected.isInstance(current)) return true;
        return false;
    }
}

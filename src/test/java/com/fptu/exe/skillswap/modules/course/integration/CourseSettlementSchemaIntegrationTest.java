package com.fptu.exe.skillswap.modules.course.integration;

import com.fptu.exe.skillswap.infrastructure.testcontainer.AbstractPostgreSQLIntegrationTest;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollmentSettlement;
import com.fptu.exe.skillswap.modules.course.domain.CourseSettlementStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentSettlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.test.database.replace=none"
})
class CourseSettlementSchemaIntegrationTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CourseEnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseEnrollmentSettlementRepository settlementRepository;

    @Test
    void settlementInsertWithoutCourseSession_shouldCommitOnPostgres() {
        UUID enrollmentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update("""
                INSERT INTO users (id, email, full_name, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, studentId, "settlement-test-" + studentId + "@example.com", "Settlement Student", now, now);
        jdbcTemplate.update("""
                INSERT INTO mentor_profiles (
                    user_id, status, session_duration, average_rating, total_reviews,
                    total_sessions, total_completed_sessions, total_rejected_bookings,
                    total_accepted_bookings, total_mentor_cancelled_bookings, is_available,
                    created_at, updated_at
                ) VALUES (?, 'ACTIVE', 60, 0.00, 0, 0, 0, 0, 0, 0, TRUE, ?, ?)
                """, studentId, now, now);
        jdbcTemplate.update("""
                INSERT INTO courses (
                    id, mentor_profile_id, subject_code, title, description,
                    max_students, total_sessions, price_scoin, reserved_count,
                    confirmed_count, status, version, created_at, updated_at,
                    total_chapters, total_lectures, total_duration_seconds,
                    average_rating, review_count, enrolled_count, total_materials
                ) VALUES (?, ?, 'SPRING', 'Settlement schema test', NULL, 0, 0, 100,
                          0, 0, 'PUBLISHED', 0, ?, ?, 0, 0, 0, 0.00, 0, 0, 0)
                """, courseId, studentId, now, now);
        jdbcTemplate.update("""
                INSERT INTO course_enrollments (
                    id, course_id, student_user_id, paid_amount_scoin, status,
                    version, enrolled_at, updated_at, base_price_scoin, buyer_fee_scoin,
                    mentor_commission_scoin, mentor_payout_scoin
                ) VALUES (?, ?, ?, 100, 'ACTIVE', 0, ?, ?, 100, 0, 0, 100)
                """, enrollmentId, courseId, studentId, now, now);

        var enrollment = enrollmentRepository.getReferenceById(enrollmentId);
        CourseEnrollmentSettlement settlement = CourseEnrollmentSettlement.builder()
                .enrollment(enrollment)
                .mentorPayoutScoin(100)
                .basePriceScoin(100)
                .buyerFeeScoin(0)
                .mentorCommissionScoin(0)
                .platformRevenueScoin(0)
                .studentRefundableScoin(100)
                .status(CourseSettlementStatus.HELD)
                .eligibleAt(now.plusSeconds(60))
                .build();

        CourseEnrollmentSettlement persisted = settlementRepository.saveAndFlush(settlement);

        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_enrollment_settlements WHERE id = ? AND course_session_id IS NULL",
                Integer.class, persisted.getId());
        assertEquals(1, sessionCount);
        assertNull(jdbcTemplate.queryForObject(
                "SELECT course_session_id FROM course_enrollment_settlements WHERE id = ?",
                UUID.class, persisted.getId()));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conrelid = 'course_enrollment_settlements'::regclass
                  AND conname = 'uk_course_settlements_enrollment_session'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = 'course_enrollment_settlements'
                  AND indexname = 'uq_course_settlement_current_enrollment'
                """, Integer.class));

        CourseEnrollmentSettlement duplicate = CourseEnrollmentSettlement.builder()
                .enrollment(enrollment)
                .mentorPayoutScoin(100)
                .basePriceScoin(100)
                .buyerFeeScoin(0)
                .mentorCommissionScoin(0)
                .platformRevenueScoin(0)
                .studentRefundableScoin(100)
                .status(CourseSettlementStatus.HELD)
                .eligibleAt(now.plusSeconds(60))
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> settlementRepository.saveAndFlush(duplicate));
    }
}

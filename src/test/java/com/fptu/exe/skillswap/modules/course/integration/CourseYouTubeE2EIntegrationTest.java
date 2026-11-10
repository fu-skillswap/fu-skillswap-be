package com.fptu.exe.skillswap.modules.course.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.infrastructure.testcontainer.AbstractPostgreSQLIntegrationTest;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateCourseChapterRequest;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateYouTubeVideoMaterialRequest;
import com.fptu.exe.skillswap.modules.course.dto.request.UpdateCourseMaterialProgressRequest;
import com.fptu.exe.skillswap.modules.course.dto.request.UpdateCourseMaterialRequest;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.test.database.replace=none",
        "application.swagger.enabled=true",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "application.openapi.version=0.1.0-beta",
        "application.openapi.include-all-group=true",
        "application.openapi.warmup.async=false",
        "application.openapi.warmup.initial-delay-ms=0",
        "application.openapi.warmup.group-delay-ms=0"
})
@AutoConfigureMockMvc
class CourseYouTubeE2EIntegrationTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID mentorUserId;
    private UUID studentUserId;
    private UUID courseId;
    private UserPrincipal mentorPrincipal;
    private UserPrincipal studentPrincipal;

    @BeforeEach
    void setupTestData() {
        mentorUserId = UUID.randomUUID();
        studentUserId = UUID.randomUUID();
        courseId = UUID.randomUUID();

        Instant now = Instant.now();
        Timestamp sqlNow = Timestamp.from(now);

        // 1. Create mentor and student users
        jdbcTemplate.update("""
                INSERT INTO users (id, email, full_name, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, mentorUserId, "mentor-" + mentorUserId + "@example.com", "Mentor Test", sqlNow, sqlNow);

        jdbcTemplate.update("""
                INSERT INTO users (id, email, full_name, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, studentUserId, "student-" + studentUserId + "@example.com", "Student Test", sqlNow, sqlNow);

        // 2. Create active mentor profile
        jdbcTemplate.update("""
                INSERT INTO mentor_profiles (
                    user_id, status, session_duration, average_rating, total_reviews,
                    total_sessions, total_completed_sessions, total_rejected_bookings,
                    total_accepted_bookings, total_mentor_cancelled_bookings, is_available,
                    created_at, updated_at
                ) VALUES (?, 'ACTIVE', 60, 5.00, 1, 1, 1, 0, 1, 0, TRUE, ?, ?)
                """, mentorUserId, sqlNow, sqlNow);

        // 3. Create published course
        jdbcTemplate.update("""
                INSERT INTO courses (
                    id, mentor_profile_id, subject_code, title, description,
                    max_students, total_sessions, price_scoin,
                    confirmed_count, status, version, created_at, updated_at,
                    total_chapters, total_lectures, total_duration_seconds,
                    average_rating, review_count, enrolled_count, total_materials
                ) VALUES (?, ?, 'PRJ301', 'Java Web with Spring Boot', 'Comprehensive course', 50, 10, 200,
                          0, 'OPEN_FOR_ENROLLMENT', 0, ?, ?, 0, 0, 0, 0.00, 0, 0, 0)
                """, courseId, mentorUserId, sqlNow, sqlNow);

        mentorPrincipal = UserPrincipal.create(mentorUserId, "mentor@test.com", List.of(RoleCode.MENTOR));
        studentPrincipal = UserPrincipal.create(studentUserId, "student@test.com", List.of(RoleCode.MENTEE));
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM course_material_progresses WHERE student_user_id = ?", studentUserId);
        jdbcTemplate.update("DELETE FROM course_progresses WHERE course_id = ?", courseId);
        jdbcTemplate.update("DELETE FROM course_enrollments WHERE course_id = ?", courseId);
        jdbcTemplate.update("DELETE FROM course_materials WHERE chapter_id IN (SELECT id FROM course_chapters WHERE course_id = ?)", courseId);
        jdbcTemplate.update("DELETE FROM course_chapters WHERE course_id = ?", courseId);
        jdbcTemplate.update("DELETE FROM courses WHERE id = ?", courseId);
        jdbcTemplate.update("DELETE FROM mentor_profiles WHERE user_id = ?", mentorUserId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", mentorUserId, studentUserId);
    }

    @Test
    @DisplayName("Full End-to-End Course YouTube Lifecycle: Creation, Anti-Leak Security, Playback, Progress & Swagger Visibility")
    void testFullCourseYouTubeFlow() throws Exception {
        // ==========================================
        // 1. MENTOR CREATES CHAPTER
        // ==========================================
        CreateCourseChapterRequest chapterReq = new CreateCourseChapterRequest("Chương 1: Cơ bản về Spring", "Nền tảng", true);
        String chapterRespStr = mockMvc.perform(post("/api/me/mentor/courses/{courseId}/chapters", courseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chapterReq))
                        .with(authentication(new UsernamePasswordAuthenticationToken(mentorPrincipal, null, mentorPrincipal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();

        JsonNode chapterNode = objectMapper.readTree(chapterRespStr);
        UUID chapterId = UUID.fromString(chapterNode.path("data").asText());

        // ==========================================
        // 2. MENTOR CREATES LOCKED YOUTUBE VIDEO (previewable = false)
        // ==========================================
        CreateYouTubeVideoMaterialRequest lockedVideoReq = new CreateYouTubeVideoMaterialRequest(
                "Bài 1: Cài đặt môi trường Spring Boot",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                600,
                1,
                false, // previewable = false
                true   // published = true
        );

        String lockedMatRespStr = mockMvc.perform(post("/api/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/video/youtube", courseId, chapterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lockedVideoReq))
                        .with(authentication(new UsernamePasswordAuthenticationToken(mentorPrincipal, null, mentorPrincipal.getAuthorities()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.title").value("Bài 1: Cài đặt môi trường Spring Boot"))
                .andExpect(jsonPath("$.data.youtubeVideoId").value("dQw4w9WgXcQ"))
                .andExpect(jsonPath("$.data.embedUrl").value("https://www.youtube.com/embed/dQw4w9WgXcQ"))
                .andExpect(jsonPath("$.data.playbackUrl").value("https://www.youtube.com/embed/dQw4w9WgXcQ"))
                .andExpect(jsonPath("$.data.storageProviderType").value("YOUTUBE"))
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
                .andReturn().getResponse().getContentAsString();

        UUID lockedMatId = UUID.fromString(objectMapper.readTree(lockedMatRespStr).path("data").path("materialId").asText());

        // ==========================================
        // 3. MENTOR CREATES PREVIEWABLE YOUTUBE VIDEO (previewable = true)
        // ==========================================
        CreateYouTubeVideoMaterialRequest previewVideoReq = new CreateYouTubeVideoMaterialRequest(
                "Bài 2: Tổng quan Spring Framework (Xem thử miễn phí)",
                "https://youtu.be/jNQXAC9IVRw",
                300,
                2,
                true,  // previewable = true
                true   // published = true
        );

        String previewMatRespStr = mockMvc.perform(post("/api/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/video/youtube", courseId, chapterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewVideoReq))
                        .with(authentication(new UsernamePasswordAuthenticationToken(mentorPrincipal, null, mentorPrincipal.getAuthorities()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.youtubeVideoId").value("jNQXAC9IVRw"))
                .andExpect(jsonPath("$.data.embedUrl").value("https://www.youtube.com/embed/jNQXAC9IVRw"))
                .andReturn().getResponse().getContentAsString();

        UUID previewMatId = UUID.fromString(objectMapper.readTree(previewMatRespStr).path("data").path("materialId").asText());

        // ==========================================
        // 4. UNENROLLED STUDENT CALLS CURRICULUM (ANTI-LEAK SECURITY VERIFICATION)
        // ==========================================
        String unenrolledCurriculumStr = mockMvc.perform(get("/api/me/courses/{courseId}/curriculum", courseId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andReturn().getResponse().getContentAsString();

        JsonNode unenrolledTree = objectMapper.readTree(unenrolledCurriculumStr).path("data").path("chapters").get(0).path("materials");

        // Material 1 (Locked): access == "LOCKED", youtubeVideoId MUST BE NULL, embedUrl MUST BE NULL
        JsonNode mat1 = unenrolledTree.get(0);
        assertThat(mat1.path("access").asText()).isEqualTo("LOCKED");
        assertThat(mat1.path("youtubeVideoId").isNull()).isTrue();
        assertThat(mat1.path("embedUrl").isNull()).isTrue();

        // Material 2 (Previewable): access == "AVAILABLE", youtubeVideoId and embedUrl ARE PRESENT
        JsonNode mat2 = unenrolledTree.get(1);
        assertThat(mat2.path("access").asText()).isEqualTo("AVAILABLE");
        assertThat(mat2.path("youtubeVideoId").asText()).isEqualTo("jNQXAC9IVRw");
        assertThat(mat2.path("embedUrl").asText()).isEqualTo("https://www.youtube.com/embed/jNQXAC9IVRw");

        // ==========================================
        // 5. UNENROLLED STUDENT ATTEMPTS PLAYBACK
        // ==========================================
        // Attempt locked lesson playback -> 403 Forbidden!
        mockMvc.perform(get("/api/me/courses/{courseId}/materials/{materialId}/playback", courseId, lockedMatId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities()))))
                .andExpect(status().isForbidden());

        // Previewable lesson playback -> 200 OK with embedUrl
        mockMvc.perform(get("/api/me/courses/{courseId}/materials/{materialId}/playback", courseId, previewMatId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.youtubeVideoId").value("jNQXAC9IVRw"))
                .andExpect(jsonPath("$.data.embedUrl").value("https://www.youtube.com/embed/jNQXAC9IVRw"))
                .andExpect(jsonPath("$.data.storageProviderType").value("YOUTUBE"));

        // ==========================================
        // 6. STUDENT ENROLLS COURSE (ACTIVE ENROLLMENT)
        // ==========================================
        UUID enrollmentId = UUID.randomUUID();
        Instant enrollNow = Instant.now();
        Timestamp sqlEnrollNow = Timestamp.from(enrollNow);
        jdbcTemplate.update("""
                INSERT INTO course_enrollments (
                    id, course_id, student_user_id, paid_amount_scoin, status,
                    version, enrolled_at, updated_at
                ) VALUES (?, ?, ?, 200, 'ACTIVE', 0, ?, ?)
                """, enrollmentId, courseId, studentUserId, sqlEnrollNow, sqlEnrollNow);

        // ==========================================
        // 7. ENROLLED STUDENT VIEWS CURRICULUM & PLAYBACK
        // ==========================================
        String enrolledCurriculumStr = mockMvc.perform(get("/api/me/courses/{courseId}/curriculum", courseId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode enrolledTree = objectMapper.readTree(enrolledCurriculumStr).path("data").path("chapters").get(0).path("materials");
        JsonNode enrolledMat1 = enrolledTree.get(0);

        // Lesson 1 is now AVAILABLE with full YouTube data!
        assertThat(enrolledMat1.path("access").asText()).isEqualTo("AVAILABLE");
        assertThat(enrolledMat1.path("youtubeVideoId").asText()).isEqualTo("dQw4w9WgXcQ");
        assertThat(enrolledMat1.path("embedUrl").asText()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");

        // Enrolled student can playback Lesson 1
        mockMvc.perform(get("/api/me/courses/{courseId}/materials/{materialId}/playback", courseId, lockedMatId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.youtubeVideoId").value("dQw4w9WgXcQ"))
                .andExpect(jsonPath("$.data.embedUrl").value("https://www.youtube.com/embed/dQw4w9WgXcQ"));

        // ==========================================
        // 8. PROGRESS TRACKING ON YOUTUBE VIDEO
        // ==========================================
        // Send 550s watched out of 600s (> 90% threshold)
        UpdateCourseMaterialProgressRequest progReq = new UpdateCourseMaterialProgressRequest(550);
        mockMvc.perform(put("/api/me/courses/{courseId}/materials/{materialId}/progress", courseId, lockedMatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progReq))
                        .with(authentication(new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities()))))
                .andExpect(status().isOk());

        // Curriculum now reflects completion
        String progressCurriculumStr = mockMvc.perform(get("/api/me/courses/{courseId}/curriculum", courseId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode progressMat1 = objectMapper.readTree(progressCurriculumStr).path("data").path("chapters").get(0).path("materials").get(0);
        assertThat(progressMat1.path("completed").asBoolean()).isTrue();
        assertThat(progressMat1.path("progressPercentage").asInt()).isGreaterThanOrEqualTo(90);

        // ==========================================
        // 9. MENTOR UPDATES YOUTUBE VIDEO LINK
        // ==========================================
        UpdateCourseMaterialRequest updateReq = new UpdateCourseMaterialRequest(
                "Bài 1: Cài đặt môi trường Spring Boot (Đã cập nhật)",
                false,
                true,
                progressMat1.path("version").asLong(),
                "https://youtu.be/9bZkp7q19f0", // new video ID: 9bZkp7q19f0
                252
        );

        mockMvc.perform(put("/api/me/mentor/courses/{courseId}/materials/{materialId}", courseId, lockedMatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq))
                        .with(authentication(new UsernamePasswordAuthenticationToken(mentorPrincipal, null, mentorPrincipal.getAuthorities()))))
                .andExpect(status().isOk());

        // Playback returns the updated YouTube video
        mockMvc.perform(get("/api/me/courses/{courseId}/materials/{materialId}/playback", courseId, lockedMatId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.youtubeVideoId").value("9bZkp7q19f0"))
                .andExpect(jsonPath("$.data.embedUrl").value("https://www.youtube.com/embed/9bZkp7q19f0"))
                .andExpect(jsonPath("$.data.durationSeconds").value(252));

        // ==========================================
        // 10. SWAGGER / OPENAPI VISIBILITY AUDIT
        // ==========================================
        String openApiJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode pathsNode = objectMapper.readTree(openApiJson).path("paths");

        // YouTube endpoint MUST be present
        assertThat(pathsNode.has("/api/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/video/youtube"))
                .as("YouTube creation endpoint must be visible in Swagger")
                .isTrue();

        // Legacy streaming endpoints MUST NOT be present
        assertThat(pathsNode.has("/api/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/video/upload-intent"))
                .as("Bunny upload intent must be hidden from Swagger")
                .isFalse();

        assertThat(pathsNode.has("/api/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/video/r2-upload-intent"))
                .as("R2 upload intent must be hidden from Swagger")
                .isFalse();

        assertThat(pathsNode.has("/api/me/mentor/courses/{courseId}/materials/{materialId}/confirm-video-upload"))
                .as("Confirm video upload must be hidden from Swagger")
                .isFalse();

        assertThat(pathsNode.has("/api/internal/video-streaming/authorize"))
                .as("Internal VPS video streaming authorize must be hidden from Swagger")
                .isFalse();

        assertThat(pathsNode.has("/api/webhooks/bunny/video-events"))
                .as("Bunny webhook callback must be hidden from Swagger")
                .isFalse();
    }
}

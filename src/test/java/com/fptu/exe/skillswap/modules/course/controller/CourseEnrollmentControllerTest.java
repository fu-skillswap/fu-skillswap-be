package com.fptu.exe.skillswap.modules.course.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.service.CourseEnrollmentService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CourseEnrollmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseEnrollmentService enrollmentService;

    @Test
    void authenticatedUserCanEnrollCourse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        Instant enrolledAt = Instant.parse("2026-09-08T10:15:30Z");
        UserPrincipal principal = UserPrincipal.create(userId, "mentee@test.com", List.of(RoleCode.MENTEE));
        Course course = Course.builder().id(courseId).title("Spring Boot").build();
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(enrollmentId)
                .course(course)
                .studentUserId(userId)
                .status(EnrollmentStatus.ACTIVE)
                .enrolledAt(enrolledAt)
                .build();

        when(enrollmentService.enrollStudent(userId, courseId)).thenReturn(enrollment);

        mockMvc.perform(post("/api/courses/{courseId}/enroll", courseId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.enrollmentId").value(enrollmentId.toString()))
                .andExpect(jsonPath("$.data.courseId").value(courseId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());

        verify(enrollmentService).enrollStudent(eq(userId), eq(courseId));
        verifyNoMoreInteractions(enrollmentService);
    }

    @Test
    void enrollmentUsesAuthenticatedUserInsteadOfClientSuppliedUserId() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID clientSuppliedUserId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(authenticatedUserId, "mentee@test.com", List.of(RoleCode.MENTEE));
        Course course = Course.builder().id(courseId).title("Spring Boot").build();
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(UUID.randomUUID())
                .course(course)
                .studentUserId(authenticatedUserId)
                .status(EnrollmentStatus.ACTIVE)
                .enrolledAt(Instant.now())
                .build();

        when(enrollmentService.enrollStudent(authenticatedUserId, courseId)).thenReturn(enrollment);

        mockMvc.perform(post("/api/courses/{courseId}/enroll", courseId)
                        .contentType("application/json")
                        .content("{\"studentUserId\":\"" + clientSuppliedUserId + "\"}")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()))))
                .andExpect(status().isCreated());

        verify(enrollmentService).enrollStudent(authenticatedUserId, courseId);
        verifyNoMoreInteractions(enrollmentService);
    }
}

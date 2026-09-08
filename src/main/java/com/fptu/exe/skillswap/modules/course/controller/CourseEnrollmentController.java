package com.fptu.exe.skillswap.modules.course.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseEnrollmentResponse;
import com.fptu.exe.skillswap.modules.course.service.CourseEnrollmentService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Tag(name = "Course enrollment", description = "Đăng ký khóa học self-paced bằng tài khoản đang đăng nhập.")
@SecurityRequirement(name = "bearerAuth")
public class CourseEnrollmentController {

    private final CourseEnrollmentService enrollmentService;

    @PostMapping("/{courseId}/enroll")
    @Operation(summary = "Đăng ký khóa học", description = "User đăng nhập tự đăng ký cho chính mình. Backend tự báo giá, thu credit, tạo enrollment ACTIVE và settlement allocation theo flow hiện có.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Đăng ký khóa học thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User đã đăng ký khóa học"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Không đủ credit hoặc payment bị từ chối")
    })
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseEnrollmentResponse> enroll(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {
        return ApiResponse.created(CourseEnrollmentResponse.from(
                enrollmentService.enrollStudent(principal.getId(), courseId)));
    }
}

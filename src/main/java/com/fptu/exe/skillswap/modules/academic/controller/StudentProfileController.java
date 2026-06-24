package com.fptu.exe.skillswap.modules.academic.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.academic.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.dto.response.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/student-profile")
@RequiredArgsConstructor
@Validated
@Tag(name = "Academic Profile", description = "Nhóm API tạo và cập nhật hồ sơ học thuật của user hiện tại. FE dùng ở bước onboarding và ở những luồng mà việc hoàn thành profile ảnh hưởng đến quyền sử dụng tính năng.")
@SecurityRequirement(name = "bearerAuth")
public class StudentProfileController {

    private final AcademicService academicService;

    @Operation(summary = "Lấy hồ sơ học thuật của tôi", description = "Trả về hồ sơ học thuật của user hiện tại. FE dùng sau bước authentication khi cần hiển thị dữ liệu profile hoặc kiểm tra xem user đã có dữ liệu onboarding hay chưa.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Hồ sơ học thuật chưa được tạo")
    })
    @GetMapping
    public ApiResponse<StudentProfileResponse> getStudentProfile(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        StudentProfileResponse response = academicService.getStudentProfile(principal.getPublicId());
        return ApiResponse.success(response);
    }

    @Operation(summary = "Lưu hồ sơ học thuật của tôi", description = "Tạo mới hoặc cập nhật hồ sơ học thuật của user hiện tại. FE dùng trong onboarding trước khi cho user vào dashboard chính, hoặc khi cần cập nhật lại dữ liệu academic ảnh hưởng tới eligibility của các tính năng. Các rule validate như student code và quan hệ program/specialization sẽ do backend kiểm tra.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lưu hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ: sai format MSSV hoặc chuyên ngành không thuộc ngành"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Cơ sở / Ngành học / Chuyên ngành không tồn tại")
    })
    @PutMapping
    public ApiResponse<StudentProfileResponse> updateStudentProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfileRequest request) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        StudentProfileResponse response = academicService.updateStudentProfile(principal.getPublicId(), request);
        return ApiResponse.success(response);
    }
}

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
@Tag(name = "Hồ sơ học thuật", description = "Quản lý hồ sơ sinh viên FPT University của người dùng đang đăng nhập")
@SecurityRequirement(name = "bearerAuth")
public class StudentProfileController {

    private final AcademicService academicService;

    @Operation(summary = "Xem hồ sơ học thuật của tôi", description = "Trả về hồ sơ học thuật đầy đủ của người dùng đang đăng nhập, "
            +
            "bao gồm mã số sinh viên, cơ sở, ngành học, chuyên ngành, học kỳ, khóa nhập học và tiểu sử. " +
            "Trả về 404 nếu người dùng chưa điền hồ sơ.")
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

    @Operation(summary = "Tạo hoặc cập nhật hồ sơ học thuật", description = "Tạo mới hoặc cập nhật hồ sơ học thuật của người dùng đang đăng nhập. "
            +
            "**Bắt buộc điền lần đầu** sau khi đăng nhập Google để được vào dashboard.\n\n" +
            "**Quy tắc mã số sinh viên:** Format `{H|S|D|Q|C}{E|S|A}` + khóa (01–22) + 4 chữ số. Ví dụ: `SE192621`, `HA221234`.\n\n"
            +
            "**Ràng buộc:** Chuyên ngành phải thuộc ngành học đã chọn.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lưu hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ: sai format MSSV, chuyên ngành không thuộc ngành, hoặc MSSV đã tồn tại"),
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

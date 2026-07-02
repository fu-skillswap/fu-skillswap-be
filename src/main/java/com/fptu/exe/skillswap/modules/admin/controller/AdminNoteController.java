package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminNoteCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminNoteListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminNoteResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminNoteService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notes")
@RequiredArgsConstructor
@Tag(name = "Admin - Notes", description = "Nhóm API nội bộ để admin ghi chú vận hành lên user, booking, report, payout và các target moderation khác.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminNoteController {

    private final AdminNoteService adminNoteService;

    @Operation(
            summary = "Lấy danh sách admin notes nội bộ",
            description = "Trả về danh sách note nội bộ có thể lọc theo targetType và targetId. Dùng cho FE admin khi cần hiển thị lịch sử ghi chú điều phối."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy notes thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin")
    })
    @GetMapping
    public ApiResponse<PageResponse<AdminNoteResponse>> getNotes(
            @ParameterObject @ModelAttribute AdminNoteListRequest request
    ) {
        return ApiResponse.success(adminNoteService.getNotes(request));
    }

    @Operation(
            summary = "Tạo admin note nội bộ",
            description = "Append-only admin note cho một target vận hành. Phase 2 không hỗ trợ sửa hoặc xóa note đã tạo."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tạo note thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Payload không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy target")
    })
    @PostMapping
    public ApiResponse<AdminNoteResponse> createNote(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminNoteCreateRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ApiResponse.success(adminNoteService.createNote(principal.getPublicId(), request));
    }
}

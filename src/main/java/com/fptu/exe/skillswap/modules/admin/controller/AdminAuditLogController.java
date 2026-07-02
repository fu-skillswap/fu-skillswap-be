package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminAuditLogListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminAuditLogItemResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminAuditLogService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Admin - Audit Logs", description = "Nhóm API read-only để admin duyệt audit logs nội bộ theo actor, entity và action mà không cần truy vấn trực tiếp database.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    @Operation(
            summary = "Lấy danh sách audit logs nội bộ",
            description = "Trả về danh sách audit logs có filter theo actor, entity, action và time range. Dữ liệu oldValue/newValue được trả nguyên trạng raw JSON/string như đang lưu trong database."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách audit logs thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin")
    })
    @GetMapping
    public ApiResponse<PageResponse<AdminAuditLogItemResponse>> getAuditLogs(
            @ParameterObject @ModelAttribute AdminAuditLogListRequest request
    ) {
        return ApiResponse.success(adminAuditLogService.getAuditLogs(request));
    }
}

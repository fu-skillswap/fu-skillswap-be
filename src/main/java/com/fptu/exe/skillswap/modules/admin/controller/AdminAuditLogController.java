package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminAuditLogListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminAuditLogItemResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminAuditLogService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
@Tag(name = "Internal/System", description = "Internal/System - không dùng cho FE người dùng. Công cụ admin tra cứu audit log kỹ thuật và lịch sử thao tác.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    @Operation(
            summary = "Lấy danh sách audit logs nội bộ",
            description = "Trả về danh sách audit logs có filter theo actor, entity, action và time range. Dữ liệu oldValue/newValue được trả nguyên trạng raw JSON/string như đang lưu trong database."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lấy danh sách audit logs thành công",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            name = "AuditLogPage",
                            value = """
                                    {"status":200,"code":"SUCCESS_0200","message":"Thành công","data":{"content":[{"auditLogId":"019f5234-aaaa-bbbb-cccc-1234567890ab","createdAt":"2026-09-04T03:40:00","actorUserId":"019f6234-aaaa-bbbb-cccc-1234567890ab","actorDisplayName":"Admin User","entityType":"FORUM_REPORT","entityId":"019f7234-aaaa-bbbb-cccc-1234567890ab","action":"UPDATE"}],"page":0,"size":20,"totalElements":1,"totalPages":1,"last":true}}
                                    """
                    ))
            ),
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

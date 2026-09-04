package com.fptu.exe.skillswap.modules.forum.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportCreateRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.modules.forum.service.ForumReportService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forum/reports")
@RequiredArgsConstructor
@Tag(name = "Forum", description = "API người dùng gửi report cho bài viết hoặc comment. Các thao tác moderation của admin nằm trong nhóm Admin - Forum.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('MENTEE','MENTOR') and !hasRole('ADMIN') and !hasRole('SYSTEM_ADMIN')")
public class ForumReportController {

    private final ForumReportService forumReportService;

    @PostMapping
    @Operation(summary = "Báo cáo bài viết hoặc comment forum", description = "Người dùng gửi một report để admin xem xét. FE không tự ẩn nội dung chỉ vì report đã tạo; trạng thái hiển thị do backend trả về ở API nội dung.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Report đã được tiếp nhận",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            name = "ForumReportCreated",
                            value = """
                                    {"status":200,"code":"SUCCESS_0200","message":"Thành công","data":{"reportId":"019f8234-aaaa-bbbb-cccc-1234567890ab","targetType":"POST","targetId":"019f5234-aaaa-bbbb-cccc-1234567890ab","reasonType":"SPAM","status":"PENDING","createdAt":"2026-09-04T03:30:00"}}
                                    """
                    ))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Target, lý do hoặc mô tả report không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Tài khoản không được phép gửi report"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy bài viết hoặc comment"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Report trùng hoặc trạng thái target không cho phép report")
    })
    public ApiResponse<ForumReportResponse> createReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ForumReportCreateRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ApiResponse.success(forumReportService.createReport(principal.getPublicId(), request));
    }
}

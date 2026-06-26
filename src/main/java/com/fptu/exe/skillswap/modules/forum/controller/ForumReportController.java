package com.fptu.exe.skillswap.modules.forum.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportCreateRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.modules.forum.service.ForumReportService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Forum", description = "Nhóm API forum nội bộ cho người dùng đăng bài, bình luận, thả reaction và đọc thảo luận theo help topic.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('MENTEE','MENTOR') and !hasRole('ADMIN') and !hasRole('SYSTEM_ADMIN')")
public class ForumReportController {

    private final ForumReportService forumReportService;

    @PostMapping
    @Operation(summary = "Report post hoặc comment forum")
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

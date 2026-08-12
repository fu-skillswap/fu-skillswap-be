package com.fptu.exe.skillswap.modules.chat.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.chat.domain.ChatReportStatus;
import com.fptu.exe.skillswap.modules.chat.dto.request.ChatReportResolveRequest;
import com.fptu.exe.skillswap.modules.chat.dto.request.ConversationLockRequest;
import com.fptu.exe.skillswap.modules.chat.dto.response.ChatReportResponse;
import com.fptu.exe.skillswap.modules.chat.service.ConversationSafetyService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/chat-reports")
@RequiredArgsConstructor
@Tag(name = "Admin Chat Moderation", description = "API moderation report cho direct booking chat")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
public class AdminChatReportController {

    private final ConversationSafetyService conversationSafetyService;

    @GetMapping
    @Operation(summary = "Danh sách chat report")
    public ApiResponse<Page<ChatReportResponse>> getReports(
            @RequestParam(required = false) ChatReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(conversationSafetyService.getReports(status, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))));
    }

    @PatchMapping("/{reportId}")
    @Operation(summary = "Resolve chat report", description = "RESOLVED_LOCKED khóa conversation hai chiều; RESOLVED_NO_ACTION chỉ đóng report.")
    public ApiResponse<ChatReportResponse> resolveReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID reportId,
            @Valid @RequestBody ChatReportResolveRequest request) {
        return ApiResponse.success(conversationSafetyService.resolveReport(reportId, principal.getId(), request));
    }

    @PatchMapping("/conversations/{conversationId}/lock")
    @Operation(summary = "Khóa hoặc mở khóa conversation", description = "Moderation override cho quyền chat. Mở khóa vẫn tôn trọng participant block và booking-derived access.")
    public ApiResponse<Void> setConversationLock(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody ConversationLockRequest request) {
        conversationSafetyService.setAdminLock(conversationId, principal.getId(), request);
        return ApiResponse.success(null);
    }
}

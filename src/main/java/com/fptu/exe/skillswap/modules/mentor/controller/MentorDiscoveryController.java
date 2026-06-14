package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorDiscoveryService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mentors")
@RequiredArgsConstructor
@Tag(name = "Mentor Discovery", description = "Các API discovery để mentee duyệt và nhận gợi ý mentor")
@SecurityRequirement(name = "bearerAuth")
public class MentorDiscoveryController {

    private final MentorDiscoveryService mentorDiscoveryService;

    @Operation(summary = "Lấy danh sách mentor gợi ý theo hồ sơ mentee hiện tại")
    @GetMapping("/recommendations")
    public ApiResponse<List<MentorRecommendationResponse>> getRecommendations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "8") int limit
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.getRecommendations(principal.getPublicId(), limit));
    }

    @Operation(summary = "Tìm kiếm và lọc danh sách mentor để browse trên trang discovery")
    @GetMapping
    public ApiResponse<PageResponse<MentorDiscoveryCardResponse>> searchMentors(
            @AuthenticationPrincipal UserPrincipal principal,
            @ModelAttribute MentorDiscoverySearchRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.searchMentors(principal.getPublicId(), request));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}

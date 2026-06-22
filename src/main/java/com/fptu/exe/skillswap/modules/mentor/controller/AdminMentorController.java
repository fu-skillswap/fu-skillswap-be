package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorListRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorListItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.service.AdminMentorService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/mentors")
@RequiredArgsConstructor
@Tag(name = "Admin Mentors", description = "API để admin theo dõi danh sách mentor trong hệ thống")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMentorController {

    private final AdminMentorService adminMentorService;

    @Operation(summary = "Xem danh sách mentor với filter/search dành cho admin")
    @GetMapping
    public ApiResponse<PageResponse<AdminMentorListItemResponse>> getMentors(
            @ParameterObject @ModelAttribute AdminMentorListRequest request
    ) {
        return ApiResponse.success(adminMentorService.getMentors(request));
    }

    @Operation(summary = "Xem chi tiết một mentor dành cho admin")
    @GetMapping("/{mentorUserId}")
    public ApiResponse<AdminMentorDetailResponse> getMentorDetail(
            @PathVariable UUID mentorUserId
    ) {
        return ApiResponse.success(adminMentorService.getMentorDetail(mentorUserId));
    }
}

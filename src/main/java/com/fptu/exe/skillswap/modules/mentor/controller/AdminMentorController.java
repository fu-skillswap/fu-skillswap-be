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
@Tag(name = "Admin - Mentors", description = "Admin list and details view for system mentors")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMentorController {

    private final AdminMentorService adminMentorService;

    @Operation(
            summary = "Xem danh sách mentor với filter/search dành cho admin",
            description = "Yêu cầu quyền ADMIN. Trả về danh sách mentor rút gọn (lightweight DTO) tối ưu cho giao diện bảng.\n\n" +
                    "**Quy tắc lọc theo trạng thái (status):**\n" +
                    "- Mặc định nếu không truyền status: Sẽ tự động ẩn các mentor ở trạng thái `DRAFT` để giữ sạch danh sách.\n" +
                    "- Nếu truyền `status=DRAFT` rõ ràng: Sẽ trả về các mentor nháp.\n\n" +
                    "Hỗ trợ tìm kiếm theo từ khóa (tên, email), lọc theo trạng thái (status), phân trang (page, size) và sắp xếp."
    )
    @GetMapping
    public ApiResponse<PageResponse<AdminMentorListItemResponse>> getMentors(
            @ParameterObject @ModelAttribute AdminMentorListRequest request
    ) {
        return ApiResponse.success(adminMentorService.getMentors(request));
    }

    @Operation(
            summary = "Xem chi tiết một mentor dành cho admin",
            description = "Yêu cầu quyền ADMIN. Trả về toàn bộ hồ sơ chi tiết của mentor bao gồm thông tin liên hệ, trạng thái tài khoản, thống kê dạy học, điểm trừ hủy lịch và các liên kết xã hội."
    )
    @GetMapping("/{mentorUserId}")
    public ApiResponse<AdminMentorDetailResponse> getMentorDetail(
            @PathVariable UUID mentorUserId
    ) {
        return ApiResponse.success(adminMentorService.getMentorDetail(mentorUserId));
    }
}

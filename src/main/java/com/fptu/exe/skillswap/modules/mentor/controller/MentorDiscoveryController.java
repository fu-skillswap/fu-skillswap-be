package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.AvailabilityQueryRequest;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorDiscoveryService;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mentors")
@RequiredArgsConstructor
@Tag(name = "Mentor Discovery", description = "Nhóm API để khám phá mentor, tìm kiếm/lọc kết quả discovery và xem thông tin public cùng review của mentor. FE dùng khi mentee đang tìm mentor trước khi tạo booking.")
@SecurityRequirement(name = "bearerAuth")
public class MentorDiscoveryController {

    private final MentorDiscoveryService mentorDiscoveryService;

    @Operation(
            summary = "Lấy danh sách mentor gợi ý",
            description = "Trả về danh sách mentor gợi ý ngắn cho user hiện tại. FE dùng ở dashboard khi cần hiển thị nhanh các mentor phù hợp trước khi user mở trang discovery đầy đủ."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách gợi ý mentor"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping("/recommendations")
    public ApiResponse<List<MentorRecommendationResponse>> getRecommendations(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "12") int limit
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.getRecommendations(principal.getPublicId(), limit));
    }

    @Operation(
            summary = "Tìm kiếm mentor",
            description = "Trả về danh sách mentor discoverable theo phân trang cho trang discovery. FE dùng cùng keyword, help topics, campus, specialization, teaching mode và các sort options hợp lệ để dựng trải nghiệm browse/filter mentor."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kết quả tìm kiếm mentor"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping
    public ApiResponse<PageResponse<MentorDiscoveryCardResponse>> searchMentors(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject @ModelAttribute MentorDiscoverySearchRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.searchMentors(principal.getPublicId(), request));
    }

    @Operation(
            summary = "Lấy chi tiết mentor",
            description = "Trả về thông tin public chi tiết của một mentor đang ở trạng thái discoverable. FE dùng sau khi user chọn một mentor card và cần xem profile, services và review trước khi chọn slot."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chi tiết mentor"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy mentor")
    })
    @GetMapping("/{mentorUserId}")
    public ApiResponse<MentorDiscoveryDetailResponse> getMentorDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID mentorUserId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.getMentorDetail(mentorUserId));
    }

    @Operation(
            summary = "Lấy danh sách slot còn hiển thị",
            description = """
                    Trả về các availability slot còn hiển thị mà FE có thể show trước khi tạo booking request.
                    FE dùng sau khi mentee mở trang mentor detail và cần danh sách slot để chọn lịch.
                    
                    Backend sẽ ẩn các slot đã ở quá khứ, inactive, đã đầy hàng chờ hoặc đã được accept bởi booking khác. Khoảng ngày được phép xem cũng bị giới hạn theo rule backend, bao gồm mốc tối đa 31 ngày hiện tại.
                    
                    Một slot đang hiển thị vẫn chưa được giữ chỗ cho mentee cho đến khi mentor accept booking request.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy availability thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy mentor hoặc mentor không ở trạng thái discoverable")
    })
    @GetMapping("/{mentorUserId}/availability")
    public ApiResponse<List<MentorAvailabilitySlotResponse>> getMentorAvailability(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID mentorUserId,
            @ParameterObject @ModelAttribute AvailabilityQueryRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.getMentorAvailability(mentorUserId, request));
    }

    @Operation(
            summary = "Lấy danh sách review của mentor",
            description = "Trả về các review công khai của một mentor đang discoverable. FE dùng ở màn mentor detail khi user cần thêm tín hiệu đánh giá trước khi tạo booking request."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách review của mentor"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy mentor")
    })
    @GetMapping("/{mentorUserId}/reviews")
    public ApiResponse<PageResponse<MentorReviewResponse>> getMentorReviews(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID mentorUserId,
            @ParameterObject @ModelAttribute BasePageRequest pageRequest
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.getMentorReviews(mentorUserId, pageRequest));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}

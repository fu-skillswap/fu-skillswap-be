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
@Tag(name = "Mentor Discovery", description = "Các API discovery để mentee duyệt và nhận gợi ý mentor")
@SecurityRequirement(name = "bearerAuth")
public class MentorDiscoveryController {

    private final MentorDiscoveryService mentorDiscoveryService;

    @Operation(summary = "Lấy danh sách gợi ý nhanh 12 mentor phù hợp nhất để hiển thị trên Dashboard")
    @GetMapping("/recommendations")
    public ApiResponse<List<MentorRecommendationResponse>> getRecommendations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "12") int limit
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.getRecommendations(principal.getPublicId(), limit));
    }

    @Operation(summary = "Tìm kiếm, lọc và xếp hạng mentor trên trang Khám phá theo relevance rồi smart matching")
    @GetMapping
    public ApiResponse<PageResponse<MentorDiscoveryCardResponse>> searchMentors(
            @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject @ModelAttribute MentorDiscoverySearchRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.searchMentors(principal.getPublicId(), request));
    }

    @Operation(summary = "Xem chi tiết một mentor đang hiển thị trên discovery")
    @GetMapping("/{mentorUserId}")
    public ApiResponse<MentorDiscoveryDetailResponse> getMentorDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID mentorUserId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.getMentorDetail(mentorUserId));
    }

    @Operation(
            summary = "Xem các slot còn hiển thị của mentor để chuẩn bị booking",
            description = """
                    Chỉ trả về các slot còn hiển thị trên discovery tại thời điểm gọi API.
                    
                    Slot sẽ không xuất hiện nếu:
                    - đã ở quá khứ
                    - inactive
                    - đã có một booking được ACCEPTED
                    - số request PENDING đã đạt giới hạn hệ thống
                    
                    Queue metadata trong từng slot:
                    - pendingRequestCount: số request PENDING hiện tại
                    - maxPendingRequests: giới hạn tối đa request PENDING cho mỗi slot
                    - remainingRequestSlots: số suất request còn có thể nhận thêm
                    
                    Lưu ý: còn xuất hiện trong availability không có nghĩa mentee chắc chắn giữ được chỗ. Chỉ khi mentor ACCEPTED thì slot mới thuộc về một booking cụ thể.
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

    @Operation(summary = "Xem các review công khai của mentor để hỗ trợ quyết định booking")
    @GetMapping("/{mentorUserId}/reviews")
    public ApiResponse<PageResponse<MentorReviewResponse>> getMentorReviews(
            @AuthenticationPrincipal UserPrincipal principal,
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

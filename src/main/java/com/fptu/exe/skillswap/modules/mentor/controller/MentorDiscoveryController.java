package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.AvailabilityQueryRequest;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorPublicAvailabilityPreviewResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.ServiceSlotCandidatesResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorDiscoveryService;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
public class MentorDiscoveryController {

    private final MentorDiscoveryService mentorDiscoveryService;
    @Operation(
            summary = "Lấy danh sách mentor gợi ý",
            description = "Trả về danh sách mentor gợi ý ngắn cho user hiện tại. FE dùng ở dashboard khi cần hiển thị nhanh các mentor phù hợp trước khi user mở trang discovery đầy đủ."
    )
    @SecurityRequirement(name = "bearerAuth")
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
            description = "Trả về danh sách mentor phù hợp theo bộ lọc. Người chưa đăng nhập có thể xem kết quả công khai; một số luồng gợi ý cần đăng nhập. FE truyền `page` bắt đầu từ 0 và `size` tối đa 50. Kết quả không có dữ liệu vẫn trả danh sách rỗng, không phải lỗi. Có thể lọc theo keyword, campus và specialization."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Kết quả tìm kiếm mentor. Không có kết quả là trạng thái bình thường, FE hiển thị empty state thay vì báo lỗi.",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "KeywordAndFilterMatch", value = """
                                    {"status":200,"code":"SUCCESS_0200","message":"Thành công","data":{"content":[{"identity":{"mentorUserId":"019f5234-aaaa-bbbb-cccc-1234567890ab","displayName":"Nguyen Van B","avatarUrl":"https://cdn.skillswap.asia/avatar/b.jpg","headline":"Backend Developer | Spring Boot Mentor","isVerified":true},"mentoring":{"expertiseDescription":"Spring Boot và REST API","foundationSupportLevel":3,"outputReviewSupportLevel":3,"directionSupportLevel":2},"reputation":{"ratingState":"RATED","ratingAverage":4.8,"reviewCount":12,"completedSessions":18},"availability":{"isAvailable":true},"match":{"score":95.5}}],"page":0,"size":12,"totalElements":1,"totalPages":1,"last":true}}
                                    """),
                            @ExampleObject(name = "NoMentorFound", value = """
                                    {"status":200,"code":"SUCCESS_0200","message":"Thành công","data":{"content":[],"page":0,"size":12,"totalElements":0,"totalPages":0,"last":true}}
                                    """)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bộ lọc không hợp lệ; kiểm tra page, size, sortBy hoặc ID filter")
    })
    @GetMapping
    public ApiResponse<PageResponse<MentorDiscoveryCardResponse>> searchMentors(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject @ModelAttribute MentorDiscoverySearchRequest request
    ) {
        return ApiResponse.success(mentorDiscoveryService.searchMentors(principal == null ? null : principal.getPublicId(), request));
    }

    @Operation(
            summary = "Lấy chi tiết mentor",
            description = "Trả về thông tin public chi tiết của một mentor đang ở trạng thái discoverable. FE dùng sau khi user chọn một mentor card và cần xem profile, services và review trước khi chọn slot."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Chi tiết mentor",
                    content = @io.swagger.v3.oas.annotations.media.Content(examples = {
                            @io.swagger.v3.oas.annotations.media.ExampleObject(
                                    name = "Mentor without reviews",
                                    value = "{\"status\":200,\"code\":\"SUCCESS\",\"data\":{\"reputation\":{\"ratingState\":\"NO_REVIEWS\",\"ratingAverage\":null,\"reviewCount\":0}}}"),
                            @io.swagger.v3.oas.annotations.media.ExampleObject(
                                    name = "Rated mentor",
                                    value = "{\"status\":200,\"code\":\"SUCCESS\",\"data\":{\"reputation\":{\"ratingState\":\"RATED\",\"ratingAverage\":4.85,\"reviewCount\":27}}}")
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy mentor")
    })
    @GetMapping("/{mentorUserId}")
    public ApiResponse<MentorDiscoveryDetailResponse> getMentorDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID mentorUserId
    ) {
        return ApiResponse.success(mentorDiscoveryService.getMentorDetail(mentorUserId));
    }

    @Operation(
            summary = "Xem trước lịch rảnh công khai của mentor",
            description = "Cho phép cả người chưa đăng nhập xem lịch rảnh sơ bộ trong tối đa hai tuần. API chỉ giúp chọn ngày/slot; không giữ chỗ, không trả booking state và chưa trả candidate segment. Sau khi đăng nhập, FE gọi API availability slots và candidates để tiếp tục đặt lịch."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lịch rảnh công khai; không có slot là kết quả hợp lệ để hiển thị empty state."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Khoảng ngày hoặc timezone filter không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy mentor")
    })
    @GetMapping("/{mentorUserId}/availability-preview")
    public ApiResponse<MentorPublicAvailabilityPreviewResponse> getPublicAvailabilityPreview(
            @PathVariable UUID mentorUserId,
            @ParameterObject @ModelAttribute AvailabilityQueryRequest request
    ) {
        return ApiResponse.success(mentorDiscoveryService.getPublicAvailabilityPreview(mentorUserId, request));
    }

    @Operation(
            summary = "Lấy danh sách availability slot còn hiển thị theo contract Phase 2",
            description = """
                    API discovery chính cho FE.
                    Trả về parent availability slots còn hiển thị, kèm danh sách service đã được mentor gắn vào từng slot.

                    API này chưa nhận serviceId vì đây là bước chọn slot cha trước.
                    Sau khi FE chọn 1 service trong danh sách services của slot, FE gọi tiếp
                    GET /api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates?serviceId=...
                    để lấy exact candidate segments của đúng service đã chọn.
                    """
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách slot cha còn hiển thị; không có slot là kết quả hợp lệ để FE hiển thị empty state.", content = @Content(examples = @ExampleObject(
            name = "Slot và service có thể chọn",
            value = """
                    {
                      "status": 200,
                      "code": "SUCCESS_0200",
                      "message": "Thành công",
                      "data": [
                        {
                          "slotId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                          "startTime": "2026-08-30T18:00:00",
                          "endTime": "2026-08-30T20:00:00",
                          "timezone": "Asia/Ho_Chi_Minh",
                          "pendingRequestCount": 0,
                          "acceptedSlotCount": 0,
                          "services": [
                            {
                              "serviceId": "019f4234-bbbb-cccc-dddd-1234567890ab",
                              "title": "Review Spring Boot",
                              "durationMinutes": 60
                            }
                          ]
                        }
                      ]
                    }
                    """))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bộ lọc ngày, timezone hoặc phân trang không hợp lệ"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy mentor"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Lịch mentor vừa thay đổi; tải lại availability trước khi chọn tiếp")
    })
    @GetMapping("/{mentorUserId}/availability-slots")
    public ApiResponse<List<MentorAvailabilitySlotResponse>> getMentorAvailabilitySlots(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID mentorUserId,
            @ParameterObject @ModelAttribute AvailabilityQueryRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.getMentorAvailability(mentorUserId, request));
    }

    @Operation(
            summary = "Lấy candidate segments của một service trong một availability slot",
            description = "FE gọi sau khi user đã chọn parent slot và selected service. Backend chỉ trả về exact candidate segments của đúng service được yêu cầu, đồng thời note rõ segment nào bị block bởi booking đã được chốt của cùng service hoặc service khác."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách candidate segment của service đã chọn", content = @Content(examples = @ExampleObject(
                    name = "Candidate có thể chọn",
                    value = """
                            {
                              "status": 200,
                              "code": "SUCCESS_0200",
                              "message": "Thành công",
                              "data": {
                                "slotId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                                "serviceId": "019f4234-bbbb-cccc-dddd-1234567890ab",
                                "serviceDurationMinutes": 60,
                                "candidateServiceSlots": [
                                  {
                                    "startTime": "2026-08-30T19:00:00",
                                    "endTime": "2026-08-30T20:00:00",
                                    "startAt": "2026-08-30T12:00:00Z",
                                    "endAt": "2026-08-30T13:00:00Z",
                                    "pendingCount": 0,
                                    "remainingPendingQuota": 3,
                                    "isSelectable": true,
                                    "reasonIfBlocked": null,
                                    "blockedByAcceptedBooking": false,
                                    "blockingBookingId": null,
                                    "blockedBySameService": false,
                                    "blockedByDifferentService": false,
                                    "bookingConflictNote": null
                                  }
                                ]
                              }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy mentor, slot hoặc service gắn với slot"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Slot hoặc service không còn khả dụng")
    })
    @GetMapping("/{mentorUserId}/availability-slots/{slotId}/candidates")
    public ApiResponse<ServiceSlotCandidatesResponse> getSlotCandidates(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID mentorUserId,
            @PathVariable UUID slotId,
            @RequestParam UUID serviceId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorDiscoveryService.getMentorAvailabilityCandidates(mentorUserId, slotId, serviceId));
    }

    @Operation(
            summary = "Lấy danh sách review của mentor",
            description = "Trả về review công khai của mentor. FE dùng ở màn chi tiết mentor trước khi tạo booking request. Danh sách dùng phân trang; không có review thì trả danh sách rỗng."
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
        return ApiResponse.success(mentorDiscoveryService.getMentorReviews(mentorUserId, pageRequest));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}

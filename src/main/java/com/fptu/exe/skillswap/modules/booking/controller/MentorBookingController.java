package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingRescheduleRequestResponse;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.service.BookingRescheduleService;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/mentor/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTOR')")
@Tag(name = "Mentor Booking", description = "Nhóm API cho toàn bộ vòng đời booking: mentee tạo request, hai bên xem chi tiết, mentor accept/reject, hai bên cancel/complete và mentor cập nhật meeting info. FE dùng nhóm này sau khi mentee đã chọn mentor, service và slot.")
@SecurityRequirement(name = "bearerAuth")
public class MentorBookingController {

    private final BookingService bookingService;
    private final BookingRescheduleService bookingRescheduleService;

    @Operation(
            summary = "Chấp nhận booking request",
            description = """
                    Chấp nhận một booking request đang PENDING thuộc về mentor hiện tại.
                    FE dùng khi mentor quyết định xác nhận một mentee cho slot đã chọn.
                    Khi accept thành công, slot sẽ được chốt, các request pending khác cùng slot sẽ bị auto reject, hệ thống đồng thời tạo session, tạo conversation và gửi notification cho các user liên quan.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chấp nhận booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không phải mentor của booking này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking không còn ở trạng thái PENDING hoặc slot đã được xử lý bởi request khác")
    })
    @PostMapping("/{bookingId}/accept")
    public ApiResponse<BookingResponse> acceptBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @RequestBody(required = false) AcceptBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.acceptBooking(principal.getPublicId(), bookingId, request));
    }

    @Operation(
            summary = "Từ chối booking request",
            description = "Từ chối một booking request đang PENDING thuộc về mentor hiện tại. FE dùng khi mentor không thể nhận buổi mentoring đó và muốn gửi lý do từ chối rõ ràng cho mentee."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Từ chối booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Thiếu rejectReason hoặc dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không phải mentor của booking này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking không còn ở trạng thái PENDING để từ chối")
    })
    @PostMapping("/{bookingId}/reject")
    public ApiResponse<BookingResponse> rejectBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody RejectBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.rejectBooking(principal.getPublicId(), bookingId, request));
    }

    @Operation(
            summary = "Hủy booking đã chấp nhận",
            description = """
                    Hủy một booking đã ACCEPTED thuộc về mentor hiện tại.
                    FE chỉ dùng API này sau khi booking đã được accept nhưng mentor cần dừng buổi mentoring đó.
                    Backend sẽ tự áp dụng penalty hoặc suspension tạm thời nếu mentor hủy quá sát giờ bắt đầu của lịch hẹn.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Mentor hủy booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Thiếu cancelReason hoặc dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không phải mentor của booking này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking không ở trạng thái cho phép mentor hủy hoặc đã quá mốc thời gian backend cho phép")
    })
    @PostMapping("/{bookingId}/cancel")
    public ApiResponse<BookingResponse> cancelBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody CancelBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.cancelBookingByMentor(principal.getPublicId(), bookingId, request));
    }

    @PostMapping("/{bookingId}/reschedule-requests")
    @Operation(
            summary = "Mentor tạo yêu cầu đổi lịch",
            description = "Tạo reschedule request cho một booking đã được accept mà user hiện tại là mentor. FE mentor dùng khi cần đề xuất khung giờ mới cho mentee xác nhận."
    )
    public ApiResponse<BookingRescheduleRequestResponse> createRescheduleRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody CreateBookingRescheduleRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.created(bookingRescheduleService.createByMentor(principal.getPublicId(), bookingId, request));
    }

    @PostMapping("/reschedule-requests/{requestId}/accept")
    @Operation(
            summary = "Mentor chấp nhận yêu cầu đổi lịch",
            description = "Mentor chấp nhận một reschedule request đang pending do mentee tạo. Backend áp dụng lịch mới nếu request còn hợp lệ và không xung đột slot."
    )
    public ApiResponse<BookingRescheduleRequestResponse> acceptRescheduleRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody RespondBookingRescheduleRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingRescheduleService.acceptByParticipant(principal.getPublicId(), requestId, request));
    }

    @PostMapping("/reschedule-requests/{requestId}/reject")
    @Operation(
            summary = "Mentor từ chối yêu cầu đổi lịch",
            description = "Mentor từ chối một reschedule request đang pending do mentee tạo. Booking hiện tại được giữ nguyên và FE có thể hiển thị lý do phản hồi nếu có."
    )
    public ApiResponse<BookingRescheduleRequestResponse> rejectRescheduleRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody RespondBookingRescheduleRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingRescheduleService.rejectByParticipant(principal.getPublicId(), requestId, request));
    }

    @Operation(
            summary = "Mentor xác nhận hoàn tất buổi mentoring",
            description = "Mentor đánh dấu buổi mentoring đã diễn ra xong. Phase 1 chuyển booking sang trạng thái chờ participant còn lại xác nhận hoặc báo issue trong 4 giờ."
    )
    @PostMapping("/{bookingId}/complete")
    public ApiResponse<BookingResponse> completeBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) CompleteBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.completeBookingByMentor(principal.getPublicId(), bookingId, request));
    }

    @Operation(
            summary = "Lưu meeting link",
            description = "Lưu hoặc cập nhật thông tin meeting cho một booking đã được mentor chấp nhận thuộc về mentor hiện tại. FE dùng sau khi booking đã được xác nhận để mentee có thể tham gia đúng platform, meeting link hoặc địa điểm offline."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lưu meeting link thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "meetingPlatform hoặc meetingLink không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không phải mentor của booking này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking chưa được xác nhận hoặc không còn cho phép cập nhật meeting link")
    })
    @PatchMapping("/{bookingId}/meeting-link")
    public ApiResponse<BookingResponse> saveMeetingLink(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody SaveMeetingLinkRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.saveMeetingLink(principal.getPublicId(), bookingId, request));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}

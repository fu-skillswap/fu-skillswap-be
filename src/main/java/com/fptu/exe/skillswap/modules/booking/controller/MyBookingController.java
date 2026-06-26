package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.ConfirmBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingRescheduleRequestResponse;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SubmitBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.service.BookingRescheduleService;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/bookings")
@RequiredArgsConstructor
@Tag(name = "Mentor Booking", description = "Nhóm API cho toàn bộ vòng đời booking: mentee tạo request, hai bên xem chi tiết, mentor accept/reject, hai bên cancel/complete và mentor cập nhật meeting info. FE dùng nhóm này sau khi mentee đã chọn mentor, service và slot.")
@SecurityRequirement(name = "bearerAuth")
public class MyBookingController {

    private final BookingService bookingService;
    private final BookingRescheduleService bookingRescheduleService;

    @Operation(
            summary = "Lấy danh sách booking của tôi",
            description = """
                    Trả về danh sách booking theo góc nhìn của user hiện tại.
                    FE dùng role=MENTEE để hiển thị các request do tôi tạo, hoặc role=MENTOR để hiển thị các request được gửi tới tôi với tư cách mentor.
                    Đây là API danh sách chính cho lịch sử booking, màn pending actions và màn theo dõi trạng thái booking.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping
    public ApiResponse<PageResponse<BookingResponse>> getMyBookings(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject @ModelAttribute BookingListRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.getMyBookings(principal.getPublicId(), request));
    }

    @Operation(
            summary = "Lấy chi tiết booking của tôi",
            description = "Trả về chi tiết một booking khi user hiện tại là mentee hoặc mentor của booking đó. FE dùng ở màn booking detail trước khi hiển thị các action như accept, reject, cancel, complete hoặc review."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy chi tiết booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking không thuộc về người dùng hiện tại"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking")
    })
    @GetMapping("/{bookingId}")
    public ApiResponse<BookingResponse> getBookingDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.getBookingDetail(principal.getPublicId(), bookingId));
    }

    @Operation(
            summary = "Hủy booking của tôi",
            description = """
                    Hủy một booking thuộc về mentee hiện tại.
                    FE dùng khi mentee không muốn tiếp tục một request đang PENDING hoặc một buổi đã ACCEPTED và phải gửi kèm lý do hủy.
                    Backend có hành vi khác nhau khi hủy booking PENDING và khi hủy booking ACCEPTED, nên FE cần handle response theo trạng thái thực tế.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hủy booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Thiếu lý do hủy hoặc dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking không thuộc về mentee hiện tại"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking không ở trạng thái có thể hủy hoặc đã quá mốc thời gian backend cho phép")
    })
    @PostMapping("/{bookingId}/cancel")
    public ApiResponse<BookingResponse> cancelBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody CancelBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.cancelBookingByMentee(principal.getPublicId(), bookingId, request));
    }

    @PostMapping("/{bookingId}/reschedule-requests")
    public ApiResponse<BookingRescheduleRequestResponse> createRescheduleRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody CreateBookingRescheduleRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.created(bookingRescheduleService.createByMentee(principal.getPublicId(), bookingId, request));
    }

    @GetMapping("/{bookingId}/reschedule-requests")
    public ApiResponse<java.util.List<BookingRescheduleRequestResponse>> getRescheduleRequests(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingRescheduleService.getMyBookingRequests(principal.getPublicId(), bookingId));
    }

    @PostMapping("/reschedule-requests/{requestId}/accept")
    public ApiResponse<BookingRescheduleRequestResponse> acceptRescheduleRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody RespondBookingRescheduleRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingRescheduleService.acceptByParticipant(principal.getPublicId(), requestId, request));
    }

    @PostMapping("/reschedule-requests/{requestId}/reject")
    public ApiResponse<BookingRescheduleRequestResponse> rejectRescheduleRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody RespondBookingRescheduleRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingRescheduleService.rejectByParticipant(principal.getPublicId(), requestId, request));
    }

    @Operation(
            summary = "Hoàn thành booking của tôi",
            description = """
                    Đánh dấu booking đã hoàn thành cho participant hiện tại.
                    Endpoint này được giữ như alias chuyển tiếp cho Phase 1:
                    - nếu current user là mentor: backend thực hiện mentor complete
                    - nếu current user là participant còn lại: backend thực hiện confirm sau session
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đánh dấu hoàn thành thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "completionNote vượt ràng buộc độ dài"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking không thuộc về người dùng hiện tại"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking chưa đủ điều kiện để complete hoặc đã hoàn thành trước đó")
    })
    @PostMapping("/{bookingId}/complete")
    public ApiResponse<BookingResponse> completeBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) CompleteBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.completeBooking(principal.getPublicId(), bookingId, request));
    }

    @Operation(
            summary = "Participant xác nhận buổi mentoring",
            description = "Participant còn lại xác nhận buổi mentoring đã diễn ra ổn sau khi mentor đã complete. Phase 1 mở sẵn contract này cho target beta mới."
    )
    @PostMapping("/{bookingId}/confirm")
    public ApiResponse<BookingResponse> confirmBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) ConfirmBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.confirmBookingByParticipant(principal.getPublicId(), bookingId, request));
    }

    @Operation(
            summary = "Participant báo vấn đề sau buổi mentoring",
            description = "Participant hợp lệ của booking báo vấn đề trong cửa sổ 24 giờ sau khi session kết thúc. Phase 1 mới chuẩn bị contract và validation skeleton cho flow này."
    )
    @PostMapping("/{bookingId}/issue")
    public ApiResponse<BookingIssueResponse> submitIssue(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody SubmitBookingIssueRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.submitBookingIssue(principal.getPublicId(), bookingId, request));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}

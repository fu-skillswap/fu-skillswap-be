package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationDocumentUploadRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationDocumentUploadIntentRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationDocumentResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationDocumentUploadIntentResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationRequestActionResult;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationProgressResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationUploadIntentStatusResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationSubmitRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationTimelineEventResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorVerificationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/mentor-verification")
@RequiredArgsConstructor
@Validated
@Tag(name = "Mentor Verification", description = "Đăng ký mentor, tải minh chứng và theo dõi kết quả duyệt.")
@SecurityRequirement(name = "bearerAuth")
/**
 * Authorization design decision:
 *
 * <p>Any authenticated user (including new mentees) may open and submit a mentor verification
 * request. This is intentional — the "apply to become a mentor" flow is open to all users
 * regardless of current role, supporting the platform's growth model.
 *
 * <p>ADMIN and SYSTEM_ADMIN are explicitly blocked because:
 * 1. System administrators should not hold a dual role as mentors (conflict of interest).
 * 2. Admin accounts interacting with the verification queue that also submit their own requests
 *    creates an audit integrity risk.
 * If an admin user wishes to mentor, they must use a separate personal account.
 *
 * <p>Users who already have the MENTOR role are not blocked — they may need to re-submit
 * verification if their status changes (e.g., after deactivation + reactivation cycle).
 */
@PreAuthorize("hasAnyRole('MENTEE', 'MENTOR')")
public class MentorVerificationController {

    private final MentorVerificationService mentorVerificationService;
    private final InMemoryRateLimitService rateLimitService;

    @Operation(summary = "Bước 1 - Mở hồ sơ đăng ký mentor", description = "Tạo hồ sơ mới hoặc trả về hồ sơ đang làm dở.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy hồ sơ xác thực mentor thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo hồ sơ xác thực mentor thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<MentorVerificationRequestResponse>> requestToBecomeMentor(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        MentorVerificationRequestActionResult<MentorVerificationRequestResponse> result =
                mentorVerificationService.requestToBecomeMentor(principal.getPublicId());
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result.data()))
                : ResponseEntity.ok(ApiResponse.success(result.data()));
    }

    @Operation(summary = "Lấy hồ sơ đăng ký mentor", description = "Dùng khi mở lại màn đăng ký hoặc xem kết quả duyệt. FE hiển thị trạng thái theo `status`: PENDING_REVIEW là đang chờ, NEEDS_REVISION là cần sửa, APPROVED là đã được duyệt và REJECTED là bị từ chối.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Hồ sơ và trạng thái hiện tại",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "PendingReview", value = """
                                    {"status":200,"code":"SUCCESS_0200","message":"Thành công","data":{"requestId":"019f5234-aaaa-bbbb-cccc-1234567890ab","status":"PENDING_REVIEW","reviewNote":null,"rejectionReason":null,"documents":[],"allowedActions":{"canEdit":false,"canSubmit":false}}}
                                    """),
                            @ExampleObject(name = "Approved", value = """
                                    {"status":200,"code":"SUCCESS_0200","message":"Thành công","data":{"requestId":"019f5234-aaaa-bbbb-cccc-1234567890ab","status":"APPROVED","reviewNote":"Hồ sơ hợp lệ.","rejectionReason":null,"documents":[],"allowedActions":{"canEdit":false,"canSubmit":false}}}
                                    """),
                            @ExampleObject(name = "Rejected", value = """
                                    {"status":200,"code":"SUCCESS_0200","message":"Thành công","data":{"requestId":"019f5234-aaaa-bbbb-cccc-1234567890ab","status":"REJECTED","reviewNote":null,"rejectionReason":"Vui lòng bổ sung minh chứng chuyên môn.","documents":[],"allowedActions":{"canEdit":true,"canSubmit":true}}}
                                    """)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping
    public ApiResponse<MentorVerificationRequestResponse> getMyRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.getMyRequest(principal.getPublicId()));
    }

    @Operation(summary = "Lấy tiến độ đăng ký mentor", description = "FE đọc kết quả này để biết bước tiếp theo cần làm.")
    @GetMapping("/progress")
    public ApiResponse<MentorVerificationProgressResponse> getMyProgress(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.getMyProgress(principal.getPublicId()));
    }

    @Operation(summary = "Lấy lịch sử xử lý hồ sơ", description = "Hiển thị các lần nộp, yêu cầu bổ sung và kết quả duyệt.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy timeline thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy hồ sơ đang hoạt động")
    })
    @GetMapping("/timeline")
    public ApiResponse<List<MentorVerificationTimelineEventResponse>> getTimeline(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.getTimeline(principal.getPublicId()));
    }

    @Operation(summary = "Lấy chi tiết một minh chứng", description = "Chỉ trả về minh chứng thuộc hồ sơ của người dùng hiện tại.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy tài liệu thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Mã tài liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tài liệu")
    })
    @GetMapping("/documents/{documentId}")
    public ApiResponse<MentorVerificationDocumentResponse> getDocument(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("documentId") UUID documentId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.getDocument(principal.getPublicId(), documentId));
    }

    @Operation(
            summary = "Bước 3 - Xác nhận minh chứng đã tải lên",
            description = "Gửi mã lượt tải lên để gắn file vào hồ sơ đăng ký."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Lưu tài liệu thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PostMapping("/documents")
    public ResponseEntity<ApiResponse<MentorVerificationRequestResponse>> uploadDocumentMetadata(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorVerificationDocumentUploadRequest request
    ) {
        ensureAuthenticated(principal);
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.TRANSFER,
                "mentor-verification:upload:" + principal.getPublicId(),
                12,
                java.time.Duration.ofMinutes(15),
                "Bạn đang upload minh chứng quá nhanh, vui lòng thử lại sau"
        );
        MentorVerificationRequestResponse response = mentorVerificationService.uploadDocument(
                principal.getPublicId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(
            summary = "Bước 2 - Tạo URL tải minh chứng",
            description = "FE gửi tên file, MIME type và kích thước. Backend trả uploadUrl tạm thời; FE upload binary lên URL này trước khi hết hạn, không lưu URL lâu dài và không tự sửa requiredHeaders."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Đã tạo upload intent",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            name = "VerificationUploadIntent",
                            value = """
                                    {"status":201,"code":"CREATED_0201","message":"Tạo mới thành công","data":{"uploadIntentId":"019f6234-aaaa-bbbb-cccc-1234567890ab","uploadUrl":"https://storage.example/upload/temporary-token","expiresAt":"2026-09-04T03:35:00Z","requiredHeaders":{"Content-Type":"application/pdf"},"status":"PENDING_UPLOAD"}}
                                    """
                    ))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Tên file, MIME type hoặc kích thước không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PostMapping("/documents/upload-intents")
    public ResponseEntity<ApiResponse<MentorVerificationDocumentUploadIntentResponse>> createDocumentUploadIntent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorVerificationDocumentUploadIntentRequest request
    ) {
        ensureAuthenticated(principal);
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.TRANSFER, "mentor-verification:upload-intent:" + principal.getPublicId(), 12,
                java.time.Duration.ofMinutes(15), "Bạn đang tạo upload intent quá nhanh, vui lòng thử lại sau");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
                mentorVerificationService.createDocumentUploadIntent(principal.getPublicId(), request)));
    }

    @Operation(summary = "Kiểm tra trạng thái tải minh chứng", description = "Dùng khi tải file bị gián đoạn hoặc người dùng quay lại màn hình.")
    @GetMapping("/documents/upload-intents/{uploadIntentId}")
    public ApiResponse<MentorVerificationUploadIntentStatusResponse> getDocumentUploadIntentStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID uploadIntentId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.getDocumentUploadIntentStatus(principal.getPublicId(), uploadIntentId));
    }

    @Operation(summary = "Tạo lại URL tải minh chứng", description = "Dùng khi URL cũ đã hết hạn hoặc bị từ chối.")
    @PostMapping("/documents/upload-intents/{uploadIntentId}/retry")
    public ResponseEntity<ApiResponse<MentorVerificationDocumentUploadIntentResponse>> retryDocumentUploadIntent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID uploadIntentId
    ) {
        ensureAuthenticated(principal);
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.TRANSFER,
                "mentor-verification:upload-intent:" + principal.getPublicId(), 12,
                java.time.Duration.ofMinutes(15), "Bạn đang tạo upload intent quá nhanh, vui lòng thử lại sau");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
                mentorVerificationService.retryDocumentUploadIntent(principal.getPublicId(), uploadIntentId)));
    }

    @Operation(
            summary = "Bước 4 - Nộp hồ sơ để admin duyệt",
            description = "Chỉ nộp được khi hồ sơ, minh chứng và điều khoản đã đầy đủ. Request hợp lệ dùng `termsAccepted: true`; sau khi nộp FE chuyển sang màn hình chờ và đọc lại status bằng GET request/progress."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Nộp hồ sơ thành công, trạng thái chuyển sang PENDING_REVIEW",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            name = "SubmitVerification",
                            value = """
                                    {"status":200,"code":"SUCCESS_0200","message":"Thành công","data":{"requestId":"019f5234-aaaa-bbbb-cccc-1234567890ab","status":"PENDING_REVIEW","submitNote":"Em đã bổ sung đầy đủ minh chứng.","estimatedReviewBy":"2026-09-05T03:21:00","reviewTargetHours":24}}
                                    """
                    ))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Hồ sơ chưa đủ điều kiện để nộp"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Hồ sơ đang ở trạng thái không cho phép nộp lại")
    })
    @PostMapping("/submit")
    public ApiResponse<MentorVerificationRequestResponse> submit(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorVerificationSubmitRequest request
    ) {
        ensureAuthenticated(principal);
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.SECURITY,
                "mentor-verification:submit:" + principal.getPublicId(),
                5,
                java.time.Duration.ofHours(1),
                "Bạn đang gửi yêu cầu xác thực quá nhanh, vui lòng kiểm tra lại hồ sơ rồi thử sau"
        );
        return ApiResponse.success(mentorVerificationService.submit(principal.getPublicId(), request));
    }

    @Operation(summary = "Xóa một minh chứng", description = "Chỉ dùng khi hồ sơ vẫn được phép chỉnh sửa.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Xóa tài liệu thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Hồ sơ hiện tại không cho phép xóa tài liệu"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tài liệu")
    })
    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<MentorVerificationRequestResponse> deleteDocument(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("documentId") UUID documentId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.deleteDocument(principal.getPublicId(), documentId));
    }

    @Operation(summary = "Rút hồ sơ đăng ký mentor", description = "Dừng quá trình đăng ký khi trạng thái hiện tại còn cho phép.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Rút hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Trạng thái hồ sơ hiện tại không cho phép rút"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy hồ sơ đang hoạt động")
    })
    @PostMapping("/withdraw")
    public ApiResponse<MentorVerificationRequestResponse> withdraw(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.withdraw(principal.getPublicId()));
    }

    @Operation(summary = "Thu hồi hồ sơ đăng ký mentor", description = "Chuyển hồ sơ từ PENDING_REVIEW về DRAFT để người dùng chỉnh sửa và nộp lại khi Admin chưa khóa duyệt.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thu hồi hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Trạng thái hồ sơ hiện tại không cho phép thu hồi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy hồ sơ đang hoạt động"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Hồ sơ đang được admin xử lý, hiện chưa thể thu hồi")
    })
    @PostMapping("/unsubmit")
    public ApiResponse<MentorVerificationRequestResponse> unsubmit(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        rateLimitService.check(
                com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.SECURITY,
                "mentor-verification:unsubmit:" + principal.getPublicId(),
                10,
                java.time.Duration.ofHours(1),
                "Bạn đang thực hiện thu hồi hồ sơ quá thường xuyên, vui lòng thử lại sau"
        );
        return ApiResponse.success(mentorVerificationService.unsubmit(principal.getPublicId()));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}

package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationDocumentResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationDocumentUploadRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationRequestActionResult;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationSubmitRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationTimelineEventResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorVerificationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/mentor-verification")
@RequiredArgsConstructor
@Validated
@Tag(name = "Mentor Verification", description = "Nhóm API mở, chỉnh sửa, nộp và theo dõi hồ sơ mentor verification cùng các minh chứng liên quan. FE dùng trong wizard xác thực mentor trước khi admin review.")
@SecurityRequirement(name = "bearerAuth")
public class MentorVerificationController {

    private final MentorVerificationService mentorVerificationService;

    @Operation(summary = "Mở mentor verification request", description = "Tạo một mentor verification request mới có thể chỉnh sửa hoặc trả về request active hiện tại nếu đã tồn tại. FE dùng đây là bước đầu tiên của wizard xác thực mentor trước khi upload minh chứng và submit.")
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

    @Operation(summary = "Lấy mentor verification mới nhất", description = "Trả về mentor verification request mới nhất của user hiện tại, bao gồm cả các request đã kết thúc. FE dùng khi cần khôi phục tiến độ wizard, trạng thái cuối, checklist và danh sách document đã upload.")
    @GetMapping
    public ApiResponse<MentorVerificationRequestResponse> getMyRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.getMyRequest(principal.getPublicId()));
    }

    @Operation(summary = "Lấy verification timeline", description = "Trả về timeline sự kiện của mentor verification request mới nhất. FE dùng để hiển thị lịch sử tiến độ xác thực như submit, request revision, approve, reject hoặc withdraw.")
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

    @Operation(summary = "Lấy chi tiết verification document", description = "Trả về metadata của một verification document thuộc request mới nhất của user hiện tại. FE dùng khi cần xem chi tiết document, review note hoặc thông tin phục vụ màn wizard verification.")
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
            summary = "Gắn metadata verification document",
            description = "Lưu metadata của verification document vào request hiện tại sau khi FE đã upload file thật lên dịch vụ lưu trữ ngoài được backend chấp nhận. API này nhận JSON metadata chứ không phải multipart upload trực tiếp, và backend vẫn kiểm tra content type, file size, host nguồn và quota theo từng loại document."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Lưu tài liệu thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "File vượt quá giới hạn cho phép"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PostMapping(path = "/documents", consumes = "application/json")
    public ResponseEntity<ApiResponse<MentorVerificationRequestResponse>> uploadDocument(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorVerificationDocumentUploadRequest request
    ) {
        ensureAuthenticated(principal);
        MentorVerificationRequestResponse response = mentorVerificationService.uploadDocument(
                principal.getPublicId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(
            summary = "Nộp mentor verification request",
            description = "Nộp mentor verification request hiện tại để admin review. FE dùng sau khi user đã hoàn thành các bước profile bắt buộc, upload đủ minh chứng cần thiết và đồng ý điều khoản mentor hiện tại. Backend sẽ từ chối submit nếu checklist chưa đủ điều kiện."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Nộp hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Hồ sơ chưa đủ điều kiện để nộp"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PostMapping("/submit")
    public ApiResponse<MentorVerificationRequestResponse> submit(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorVerificationSubmitRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.submit(principal.getPublicId(), request));
    }

    @Operation(summary = "Xóa verification document", description = "Xóa mềm một verification document khỏi request hiện tại còn cho phép chỉnh sửa. FE dùng ở trạng thái draft hoặc revision khi user muốn thay thế hoặc bỏ một minh chứng trước khi submit lại.")
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

    @Operation(summary = "Rút mentor verification request", description = "Rút mentor verification request mới nhất khi trạng thái hiện tại còn cho phép dừng flow. FE dùng khi user không muốn tiếp tục xác thực nữa; nếu request đang bị admin lock hợp lệ thì backend có thể chặn thao tác này.")
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

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}

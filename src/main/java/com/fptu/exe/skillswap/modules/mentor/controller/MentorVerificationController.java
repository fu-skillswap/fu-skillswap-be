package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationDocumentResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationRequestActionResult;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationSubmitRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationTimelineEventResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorVerificationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/mentor-verification")
@RequiredArgsConstructor
@Validated
@Tag(name = "Xác thực mentor", description = "Luồng tạo hồ sơ xác thực mentor, tải minh chứng và nộp hồ sơ duyệt")
@SecurityRequirement(name = "bearerAuth")
public class MentorVerificationController {

    private final MentorVerificationService mentorVerificationService;

    @Operation(summary = "Khởi tạo hoặc lấy hồ sơ xác thực mentor đang hoạt động")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy hồ sơ xác thực mentor thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo hồ sơ xác thực mentor thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<MentorVerificationRequestResponse>> requestToBecomeMentor(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        MentorVerificationRequestActionResult<MentorVerificationRequestResponse> result =
                mentorVerificationService.requestToBecomeMentor(principal.getPublicId());
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result.data()))
                : ResponseEntity.ok(ApiResponse.success(result.data()));
    }

    @Operation(summary = "Xem hồ sơ xác thực mentor hiện tại")
    @GetMapping
    public ApiResponse<MentorVerificationRequestResponse> getMyRequest(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.getMyRequest(principal.getPublicId()));
    }

    @Operation(summary = "Xem timeline hồ sơ xác thực mentor hiện tại")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy timeline thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy hồ sơ đang hoạt động")
    })
    @GetMapping("/timeline")
    public ApiResponse<List<MentorVerificationTimelineEventResponse>> getTimeline(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.getTimeline(principal.getPublicId()));
    }

    @Operation(summary = "Xem chi tiết một tài liệu xác thực mentor")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy tài liệu thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Mã tài liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tài liệu")
    })
    @GetMapping("/documents/{documentId}")
    public ApiResponse<MentorVerificationDocumentResponse> getDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("documentId") UUID documentId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.getDocument(principal.getPublicId(), documentId));
    }

    @Operation(
            summary = "Tải minh chứng xác thực mentor",
            description = "Chấp nhận JPG hoặc PNG. Minh chứng xác thực mentor sẽ được lưu qua Cloudinary."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tải tài liệu thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MentorVerificationRequestResponse>> uploadDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("documentType") VerificationDocumentType documentType,
            @RequestPart("file") MultipartFile file
    ) {
        ensureAuthenticated(principal);
        MentorVerificationRequestResponse response = mentorVerificationService.uploadDocument(
                principal.getPublicId(),
                documentType,
                file
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(
            summary = "Nộp hồ sơ xác thực mentor để admin duyệt",
            description = "Chỉ cho phép nộp khi người dùng đã hoàn tất hồ sơ học thuật và hồ sơ mentor, "
                    + "đồng thời đã hoàn tất mentor profile, tải đủ minh chứng FPTU và minh chứng năng lực mentoring, "
                    + "và đã đồng ý điều khoản mentor của SkillSwap."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Nộp hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Hồ sơ chưa đủ điều kiện để nộp"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PostMapping("/submit")
    public ApiResponse<MentorVerificationRequestResponse> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorVerificationSubmitRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.submit(principal.getPublicId(), request));
    }

    @Operation(summary = "Xóa mềm một tài liệu xác thực mentor")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Xóa tài liệu thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Hồ sơ hiện tại không cho phép xóa tài liệu"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tài liệu")
    })
    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<MentorVerificationRequestResponse> deleteDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("documentId") UUID documentId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorVerificationService.deleteDocument(principal.getPublicId(), documentId));
    }

    @Operation(summary = "Rút hồ sơ xác thực mentor hiện tại")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Rút hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Trạng thái hồ sơ hiện tại không cho phép rút"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy hồ sơ đang hoạt động")
    })
    @PostMapping("/withdraw")
    public ApiResponse<MentorVerificationRequestResponse> withdraw(
            @AuthenticationPrincipal UserPrincipal principal
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

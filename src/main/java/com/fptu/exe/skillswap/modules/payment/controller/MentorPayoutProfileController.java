package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.payment.dto.request.MentorPayoutProfileUpsertRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.MentorPayoutProfileResponse;
import com.fptu.exe.skillswap.modules.payment.service.MentorPayoutProfileService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mentor/payout-profiles")
@Tag(name = "Mentor Payout Profiles", description = "Nhóm API quản lý tài khoản nhận tiền payout của mentor.")
@SecurityRequirement(name = "bearerAuth")
public class MentorPayoutProfileController {

    private final MentorPayoutProfileService payoutProfileService;

    @Operation(summary = "Tạo payout profile", description = "Mentor tạo tài khoản nhận tiền để dùng cho các payout request sau này.")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping
    public ResponseEntity<ApiResponse<MentorPayoutProfileResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorPayoutProfileUpsertRequest request) {
        ensureAuthenticated(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(payoutProfileService.create(principal.getPublicId(), request)));
    }

    @Operation(summary = "Cập nhật payout profile", description = "Mentor cập nhật bank info đã lưu.")
    @PreAuthorize("hasRole('MENTOR')")
    @PutMapping("/{payoutProfileId}")
    public ResponseEntity<ApiResponse<MentorPayoutProfileResponse>> update(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID payoutProfileId,
            @Valid @RequestBody MentorPayoutProfileUpsertRequest request) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(
                payoutProfileService.update(principal.getPublicId(), payoutProfileId, request)
        ));
    }

    @Operation(summary = "Lấy danh sách payout profile", description = "Mentor xem các tài khoản nhận tiền đang lưu.")
    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MentorPayoutProfileResponse>>> listMine(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(payoutProfileService.getMine(principal.getPublicId())));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}

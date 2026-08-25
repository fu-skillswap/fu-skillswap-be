package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminBookingIssueEvidenceVisibilityRequest;
import com.fptu.exe.skillswap.modules.admin.service.AdminAuditWriterService;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueDetailResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueEvidenceDownloadResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueEvidenceResponse;
import com.fptu.exe.skillswap.modules.booking.service.BookingIssueEvidenceService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
@Tag(name = "Admin - Booking dispute evidence")
@SecurityRequirement(name = "bearerAuth")
public class AdminBookingIssueEvidenceController {
    private final BookingIssueEvidenceService evidenceService;
    private final AdminAuditWriterService auditWriter;

    @GetMapping("/{bookingId}/issue/detail")
    public ApiResponse<BookingIssueDetailResponse> detail(@PathVariable UUID bookingId) {
        return ApiResponse.success(evidenceService.getForAdmin(bookingId));
    }

    @GetMapping("/{bookingId}/issue/evidence/{evidenceId}/download")
    public ApiResponse<BookingIssueEvidenceDownloadResponse> download(@PathVariable UUID bookingId, @PathVariable UUID evidenceId) {
        return ApiResponse.success(evidenceService.downloadForAdmin(bookingId, evidenceId));
    }

    @PostMapping("/{bookingId}/issue/evidence/{evidenceId}/visibility")
    @com.fptu.exe.skillswap.shared.idempotency.Idempotent
    public ApiResponse<BookingIssueEvidenceResponse> visibility(@AuthenticationPrincipal UserPrincipal principal,
                                                                  @PathVariable UUID bookingId, @PathVariable UUID evidenceId,
                                                                  @Valid @RequestBody AdminBookingIssueEvidenceVisibilityRequest request) {
        BookingIssueEvidenceResponse response = evidenceService.setAdminVisibility(bookingId, evidenceId, principal.getPublicId(), request.hidden(), request.reason());
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("bookingId", bookingId);
        if (request.reason() != null) auditPayload.put("reason", request.reason());
        auditWriter.writeOperatorEvent(principal.getPublicId(), "BOOKING_ISSUE_EVIDENCE", evidenceId,
                request.hidden() ? "HIDE_EVIDENCE" : "UNHIDE_EVIDENCE", null, auditPayload);
        return ApiResponse.success(response);
    }
}

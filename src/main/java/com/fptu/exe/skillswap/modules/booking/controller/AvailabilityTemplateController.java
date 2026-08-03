package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityTemplateConfiguredStatus;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityTemplateEffectiveStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.*;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityTemplateResponse;
import com.fptu.exe.skillswap.modules.booking.service.AvailabilityTemplateService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/availability-templates")
@PreAuthorize("hasRole('MENTOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Availability Templates")
@RequiredArgsConstructor
public class AvailabilityTemplateController {
    private final AvailabilityTemplateService templateService;

    @PostMapping @Operation(summary = "Create weekly availability template")
    public ApiResponse<AvailabilityTemplateResponse> create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateAvailabilityTemplateRequest request) { return ApiResponse.success(templateService.create(id(principal), request)); }
    @GetMapping @Operation(summary = "List mentor availability templates")
    public ApiResponse<CursorPageResponse<AvailabilityTemplateResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) AvailabilityTemplateConfiguredStatus configuredStatus,
            @RequestParam(required = false) AvailabilityTemplateEffectiveStatus effectiveStatus,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) { return ApiResponse.success(templateService.list(id(principal), configuredStatus, effectiveStatus, cursor, limit)); }
    @GetMapping("/{templateId}") @Operation(summary = "Get availability template")
    public ApiResponse<AvailabilityTemplateResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId) { return ApiResponse.success(templateService.get(id(principal), templateId)); }
    @PutMapping("/{templateId}") @Operation(summary = "Update availability template")
    public ApiResponse<AvailabilityTemplateResponse> update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @Valid @RequestBody UpdateAvailabilityTemplateRequest request) { return ApiResponse.success(templateService.update(id(principal), templateId, request)); }
    @PostMapping("/{templateId}/pause") @Operation(summary = "Pause availability template")
    public ApiResponse<AvailabilityTemplateResponse> pause(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @Valid @RequestBody AvailabilityTemplateVersionRequest request) { return ApiResponse.success(templateService.pause(id(principal), templateId, request)); }
    @PostMapping("/{templateId}/resume") @Operation(summary = "Resume availability template")
    public ApiResponse<AvailabilityTemplateResponse> resume(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @Valid @RequestBody AvailabilityTemplateVersionRequest request) { return ApiResponse.success(templateService.resume(id(principal), templateId, request)); }
    @PostMapping("/{templateId}/archive") @Operation(summary = "Archive availability template")
    public ApiResponse<AvailabilityTemplateResponse> archive(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @Valid @RequestBody AvailabilityTemplateVersionRequest request) { return ApiResponse.success(templateService.archive(id(principal), templateId, request)); }
    @PutMapping("/{templateId}/exceptions/{occurrenceDate}") @Operation(summary = "Skip a template occurrence")
    public ApiResponse<AvailabilityTemplateResponse> addException(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurrenceDate, @Valid @RequestBody AvailabilityTemplateExceptionRequest request) { return ApiResponse.success(templateService.addException(id(principal), templateId, occurrenceDate, request)); }
    @PostMapping("/{templateId}/exceptions/{occurrenceDate}/restore") @Operation(summary = "Restore skipped template occurrence")
    public ApiResponse<AvailabilityTemplateResponse> restoreException(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurrenceDate, @Valid @RequestBody AvailabilityTemplateVersionRequest request) { return ApiResponse.success(templateService.restoreException(id(principal), templateId, occurrenceDate, request)); }
    private UUID id(UserPrincipal principal) { if (principal == null) throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng"); return principal.getPublicId(); }
}

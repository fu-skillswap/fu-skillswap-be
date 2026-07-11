package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateAvailabilitySlotRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpdateAvailabilitySlotRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.MentorManagedAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/availability-slots")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTOR')")
@Tag(name = "Mentor Availability Slot", description = "API quản lý trực tiếp các slot rảnh của Mentor (CRUD).")
@SecurityRequirement(name = "bearerAuth")
public class MentorAvailabilitySlotController {

    private final MentorAvailabilityService mentorAvailabilityService;

    @Operation(
            summary = "Tạo trực tiếp một slot rảnh cho mentor",
            description = "Mentor tạo slot rảnh cụ thể theo ngày giờ. Hệ thống tự động tạo liên kết ngầm với availability-rule ẩn."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MentorManagedAvailabilitySlotResponse> createSlot(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAvailabilitySlotRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorAvailabilityService.createSlotDirectly(principal.getPublicId(), request));
    }

    @Operation(
            summary = "Lấy danh sách các slot rảnh của mentor hiện tại",
            description = "Lấy danh sách các slot rảnh để hiển thị trên màn hình quản lý lịch của mentor."
    )
    @GetMapping
    public ApiResponse<List<MentorManagedAvailabilitySlotResponse>> getMySlots(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorAvailabilityService.getMySlots(principal.getPublicId(), fromDate, toDate));
    }

    @Operation(
            summary = "Cập nhật slot rảnh",
            description = "Cập nhật ngày giờ, ghi chú hoặc danh sách services gắn kèm của slot. Chỉ áp dụng khi slot chưa được đặt (isBooked = false)."
    )
    @PutMapping("/{slotId}")
    public ApiResponse<MentorManagedAvailabilitySlotResponse> updateSlot(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID slotId,
            @Valid @RequestBody UpdateAvailabilitySlotRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorAvailabilityService.updateSlotDirectly(principal.getPublicId(), slotId, request));
    }

    @Operation(
            summary = "Xóa/hủy slot rảnh của mentor",
            description = "Xóa slot rảnh. Nếu slot có các booking đang chờ duyệt (PENDING), các booking đó sẽ tự động bị từ chối."
    )
    @DeleteMapping("/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteSlot(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID slotId
    ) {
        ensureAuthenticated(principal);
        mentorAvailabilityService.deleteSlotDirectly(principal.getPublicId(), slotId);
        return ApiResponse.success(null);
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}

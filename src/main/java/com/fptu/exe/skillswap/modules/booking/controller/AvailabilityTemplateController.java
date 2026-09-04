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
@Tag(name = "Availability Templates", description = "Quản lý mẫu lịch rảnh lặp lại của mentor. API yêu cầu tài khoản mentor và dùng múi giờ đã cấu hình trong mẫu lịch.")
@RequiredArgsConstructor
public class AvailabilityTemplateController {
    private final AvailabilityTemplateService templateService;

    @PostMapping
    @Operation(summary = "Tạo mẫu lịch rảnh hằng tuần", description = "Mentor gọi API này để tạo lịch rảnh lặp lại theo tuần. Kết quả trả về mẫu lịch và phiên bản hiện tại của mẫu.")
    public ApiResponse<AvailabilityTemplateResponse> create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateAvailabilityTemplateRequest request) { return ApiResponse.success(templateService.create(id(principal), request)); }
    @GetMapping
    @Operation(summary = "Lấy danh sách mẫu lịch rảnh", description = "Trả về các mẫu lịch rảnh của mentor hiện tại. Có thể lọc theo trạng thái và dùng cursor để lấy trang tiếp theo; nếu chưa có mẫu, danh sách items rỗng.")
    public ApiResponse<CursorPageResponse<AvailabilityTemplateResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) AvailabilityTemplateConfiguredStatus configuredStatus,
            @RequestParam(required = false) AvailabilityTemplateEffectiveStatus effectiveStatus,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) { return ApiResponse.success(templateService.list(id(principal), configuredStatus, effectiveStatus, cursor, limit)); }
    @GetMapping("/{templateId}")
    @Operation(summary = "Lấy chi tiết mẫu lịch rảnh", description = "Dùng khi cần hiển thị hoặc kiểm tra một mẫu lịch cụ thể của mentor hiện tại.")
    public ApiResponse<AvailabilityTemplateResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId) { return ApiResponse.success(templateService.get(id(principal), templateId)); }
    @PutMapping("/{templateId}")
    @Operation(summary = "Cập nhật mẫu lịch rảnh", description = "Cập nhật ngày, khung giờ hoặc thông tin cấu hình của mẫu lịch. Gửi đúng version hiện tại nếu request yêu cầu version.")
    public ApiResponse<AvailabilityTemplateResponse> update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @Valid @RequestBody UpdateAvailabilityTemplateRequest request) { return ApiResponse.success(templateService.update(id(principal), templateId, request)); }
    @PostMapping("/{templateId}/pause")
    @Operation(summary = "Tạm dừng mẫu lịch rảnh", description = "Tạm dừng việc tạo các lịch rảnh mới từ mẫu. Dùng khi mentor chưa muốn nhận lịch nhưng chưa muốn xóa cấu hình.")
    public ApiResponse<AvailabilityTemplateResponse> pause(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @Valid @RequestBody AvailabilityTemplateVersionRequest request) { return ApiResponse.success(templateService.pause(id(principal), templateId, request)); }
    @PostMapping("/{templateId}/resume")
    @Operation(summary = "Tiếp tục mẫu lịch rảnh", description = "Bật lại mẫu lịch đã tạm dừng để tiếp tục tạo các lịch rảnh theo cấu hình.")
    public ApiResponse<AvailabilityTemplateResponse> resume(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @Valid @RequestBody AvailabilityTemplateVersionRequest request) { return ApiResponse.success(templateService.resume(id(principal), templateId, request)); }
    @PostMapping("/{templateId}/archive")
    @Operation(summary = "Lưu trữ mẫu lịch rảnh", description = "Ngừng sử dụng mẫu lịch rảnh này về sau. Chỉ gọi khi mentor chắc chắn không cần cấu hình này nữa.")
    public ApiResponse<Void> archive(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @Valid @RequestBody AvailabilityTemplateVersionRequest request) {
        templateService.archive(id(principal), templateId, request);
        return ApiResponse.success(null);
    }
    @PutMapping("/{templateId}/exceptions/{occurrenceDate}")
    @Operation(summary = "Bỏ qua một ngày trong mẫu lịch", description = "Bỏ qua một ngày cụ thể được tạo từ mẫu lịch, ví dụ ngày mentor nghỉ. Thao tác này chỉ áp dụng cho ngày đã chọn.")
    public ApiResponse<AvailabilityTemplateResponse> addException(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurrenceDate, @Valid @RequestBody AvailabilityTemplateExceptionRequest request) { return ApiResponse.success(templateService.addException(id(principal), templateId, occurrenceDate, request)); }
    @PostMapping("/{templateId}/exceptions/{occurrenceDate}/restore")
    @Operation(summary = "Khôi phục ngày đã bỏ qua", description = "Khôi phục một ngày trước đó đã được đánh dấu bỏ qua để lịch rảnh tiếp tục áp dụng theo mẫu.")
    public ApiResponse<AvailabilityTemplateResponse> restoreException(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID templateId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurrenceDate, @Valid @RequestBody AvailabilityTemplateVersionRequest request) { return ApiResponse.success(templateService.restoreException(id(principal), templateId, occurrenceDate, request)); }
    private UUID id(UserPrincipal principal) { if (principal == null) throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng"); return principal.getPublicId(); }
}

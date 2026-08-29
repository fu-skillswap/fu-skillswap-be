package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fptu.exe.skillswap.shared.time.FlexibleInstantDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Payload cập nhật slot rảnh cho mentor")
public record UpdateAvailabilitySlotRequest(
        @Schema(description = "Thời gian bắt đầu mới (UTC Instant '2026-06-29T02:00:00Z' hoặc giờ VN '2026-06-29T09:00:00')", example = "2026-06-29T09:00:00")
        @NotNull(message = "startAt là bắt buộc")
        @JsonDeserialize(using = FlexibleInstantDeserializer.class)
        Instant startAt,

        @Schema(description = "Thời gian kết thúc mới (UTC Instant '2026-06-29T04:00:00Z' hoặc giờ VN '2026-06-29T11:00:00')", example = "2026-06-29T11:00:00")
        @NotNull(message = "endAt là bắt buộc")
        @JsonDeserialize(using = FlexibleInstantDeserializer.class)
        Instant endAt,

        @Schema(description = "Ghi chú mới cho slot rảnh", example = "Thay đổi giờ rảnh")
        @Size(max = 200, message = "note không được vượt quá 200 ký tự")
        String note,

        @Schema(description = "Tập service mới", example = "[\"019f09d4-8eb0-7952-a820-0808734f7696\"]")
        @NotEmpty(message = "serviceIds không được để trống")
        List<@NotNull UUID> serviceIds,

        @NotNull(message = "expectedVersion là bắt buộc")
        @PositiveOrZero(message = "expectedVersion không hợp lệ")
        Integer expectedVersion,

        Boolean rejectPendingBookings,
        String pendingRejectionToken,

        Boolean replaceGeneratedOccurrences,
        List<@NotNull ExpectedTemplateVersionRequest> expectedTemplateVersions,

        @JsonIgnore
        boolean legacyJavaBridge
) {
    public UpdateAvailabilitySlotRequest(Instant startAt, Instant endAt, String note, List<UUID> serviceIds,
                                         Integer expectedVersion, Boolean rejectPendingBookings, String pendingRejectionToken) {
        this(startAt, endAt, note, serviceIds, expectedVersion, rejectPendingBookings, pendingRejectionToken, false, List.of(), false);
    }
    /** Java-only bridge; HTTP callers must provide optimistic-version fields. */
    @Deprecated(forRemoval = true)
    public UpdateAvailabilitySlotRequest(java.time.LocalDateTime startAt, java.time.LocalDateTime endAt,
                                         String note, List<UUID> serviceIds) {
        this(startAt == null ? null : startAt.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),
                endAt == null ? null : endAt.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), note, serviceIds, 0, false, null, false, List.of(), true);
    }

    @Deprecated(forRemoval = true)
    public java.time.LocalDateTime startTime() {
        return startAt == null ? null : java.time.LocalDateTime.ofInstant(startAt, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Deprecated(forRemoval = true)
    public java.time.LocalDateTime endTime() {
        return endAt == null ? null : java.time.LocalDateTime.ofInstant(endAt, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}

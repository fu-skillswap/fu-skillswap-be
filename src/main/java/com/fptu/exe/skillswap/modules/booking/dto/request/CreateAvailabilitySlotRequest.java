package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Payload tạo slot rảnh trực tiếp cho mentor")
public record CreateAvailabilitySlotRequest(
        @Schema(description = "UTC whole-minute start của slot", example = "2026-06-29T01:00:00Z")
        @NotNull(message = "startAt là bắt buộc")
        Instant startAt,

        @Schema(description = "UTC whole-minute end của slot", example = "2026-06-29T03:00:00Z")
        @NotNull(message = "endAt là bắt buộc")
        Instant endAt,

        @Schema(description = "Ghi chú nội bộ cho slot rảnh này", example = "Rảnh buổi tối để tư vấn CV")
        @Size(max = 200, message = "note không được vượt quá 200 ký tự")
        String note,

        @Schema(description = "Tập service bắt buộc được gắn vào slot", example = "[\"019f09d4-8eb0-7952-a820-0808734f7696\"]")
        @NotEmpty(message = "serviceIds không được để trống")
        List<@NotNull UUID> serviceIds,

        @JsonIgnore
        boolean legacyJavaBridge
) {
    public CreateAvailabilitySlotRequest(Instant startAt, Instant endAt, String note, List<UUID> serviceIds) {
        this(startAt, endAt, note, serviceIds, false);
    }
    /** Java-only bridge; HTTP callers use Instant UTC boundaries. */
    @Deprecated(forRemoval = true)
    public CreateAvailabilitySlotRequest(java.time.LocalDateTime startAt, java.time.LocalDateTime endAt,
                                         String note, List<UUID> serviceIds) {
        this(startAt == null ? null : startAt.toInstant(java.time.ZoneOffset.UTC),
                endAt == null ? null : endAt.toInstant(java.time.ZoneOffset.UTC), note, serviceIds, true);
    }

    @Deprecated(forRemoval = true)
    public java.time.LocalDateTime startTime() {
        return startAt == null ? null : java.time.LocalDateTime.ofInstant(startAt, java.time.ZoneOffset.UTC);
    }

    @Deprecated(forRemoval = true)
    public java.time.LocalDateTime endTime() {
        return endAt == null ? null : java.time.LocalDateTime.ofInstant(endAt, java.time.ZoneOffset.UTC);
    }

}

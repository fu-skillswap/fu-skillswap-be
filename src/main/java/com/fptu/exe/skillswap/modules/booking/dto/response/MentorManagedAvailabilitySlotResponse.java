package com.fptu.exe.skillswap.modules.booking.dto.response;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AvailabilitySlotServiceBasicResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.SlotMutationCapabilityResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Slot availability của mentor sau khi backend cập nhật danh sách service được gắn vào slot")
public record MentorManagedAvailabilitySlotResponse(
        UUID slotId,
        Instant startAt,
        Instant endAt,
        String timezone,
        boolean isActive,
        String note,
        List<AvailabilitySlotServiceBasicResponse> services,
        Integer version,
        int pendingBookingCount,
        int lockingBookingCount,
        boolean hasLockingBooking,
        SlotMutationCapabilityResponse timeMutation,
        SlotMutationCapabilityResponse deactivation,
        boolean canEditNote
) {
    @Deprecated(forRemoval = true)
    public java.time.LocalDateTime startTime() {
        return startAt == null ? null : java.time.LocalDateTime.ofInstant(startAt, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Deprecated(forRemoval = true)
    public java.time.LocalDateTime endTime() {
        return endAt == null ? null : java.time.LocalDateTime.ofInstant(endAt, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}

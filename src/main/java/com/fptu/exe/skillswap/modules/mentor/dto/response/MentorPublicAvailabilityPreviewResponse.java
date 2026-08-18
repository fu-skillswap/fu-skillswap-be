package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Lịch công khai chỉ để xem trước; khoảng booking chính xác vẫn cần xác thực. */
@Schema(description = "Lịch rảnh công khai không bao gồm quota, request hoặc trạng thái booking nội bộ.")
public record MentorPublicAvailabilityPreviewResponse(
        String timezone,
        boolean isPublicOfferAvailable,
        LocalDateTime nextAvailableAt,
        List<Slot> slots
) {
    public MentorPublicAvailabilityPreviewResponse {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    public record Slot(LocalDateTime startTime, LocalDateTime endTime, List<Service> services) {
        public Slot {
            services = services == null ? List.of() : List.copyOf(services);
        }
    }

    public record Service(UUID serviceId, String title, Integer durationMinutes, boolean isFree, Integer priceScoin) {
    }
}

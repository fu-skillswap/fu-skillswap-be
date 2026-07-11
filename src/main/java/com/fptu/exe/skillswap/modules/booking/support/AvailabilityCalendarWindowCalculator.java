package com.fptu.exe.skillswap.modules.booking.support;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Component
@Deprecated(forRemoval = false)
public class AvailabilityCalendarWindowCalculator {

    public DateRange currentVisibleRange(LocalDate today) {
        LocalDate cycleStart = mondayOfCurrentWeek(today);
        return new DateRange(cycleStart, cycleStart.plusDays(13));
    }

    public boolean shouldPrepareNextCycle(LocalDate today) {
        DateRange currentRange = currentVisibleRange(today);
        LocalDate secondWeekSaturday = currentRange.endDate().minusDays(1);
        return !today.isBefore(secondWeekSaturday);
    }

    public DateRange nextPreparationRange(LocalDate today) {
        DateRange currentRange = currentVisibleRange(today);
        LocalDate nextCycleStart = currentRange.endDate().plusDays(1);
        return new DateRange(nextCycleStart, nextCycleStart.plusDays(13));
    }

    public DateRange resolveClientQueryRange(LocalDate today, LocalDate fromDate, LocalDate toDate) {
        DateRange visibleRange = currentVisibleRange(today);
        LocalDate resolvedFrom = fromDate == null ? visibleRange.startDate() : fromDate;
        LocalDate resolvedTo = toDate == null ? visibleRange.endDate() : toDate;

        if (resolvedTo.isBefore(resolvedFrom)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "toDate phải lớn hơn hoặc bằng fromDate");
        }
        if (resolvedFrom.isBefore(visibleRange.startDate()) || resolvedTo.isAfter(visibleRange.endDate())) {
            throw new BaseException(
                    ErrorCode.BAD_REQUEST,
                    "Chỉ được xem availability trong phạm vi từ Thứ 2 tuần hiện tại đến Chủ nhật tuần sau"
            );
        }
        return new DateRange(resolvedFrom, resolvedTo);
    }

    private LocalDate mondayOfCurrentWeek(LocalDate today) {
        if (today == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không xác định được ngày hiện tại để tính lịch availability");
        }
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}

package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminQueueCaseListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminDashboardMentorVerificationOverviewResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminDashboardOverviewResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminDashboardQueueItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminDashboardQueuesResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminDashboardTimeseriesPointResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminDashboardTimeseriesResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminDashboardUsersOverviewResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminQueueCaseItemResponse;
import com.fptu.exe.skillswap.modules.admin.repository.AdminDashboardQueryRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequestStatus;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final String TIMEZONE = DateTimeUtil.ZONE_HCM;

    private final AdminDashboardQueryRepository adminDashboardQueryRepository;
    private final AdminQueueWorkbenchService adminQueueWorkbenchService;

    public AdminDashboardOverviewResponse getOverview() {
        LocalDateTime snapshotAt = DateTimeUtil.now();
        var userCounts = adminDashboardQueryRepository.fetchUserOverviewCounts();
        Map<String, Long> mentorVerificationCounts = toStatusCountMap(
                VerificationStatus.class,
                adminDashboardQueryRepository.countMentorVerificationByStatus()
        );
        Map<String, Long> bookingCounts = toStatusCountMap(
                BookingStatus.class,
                adminDashboardQueryRepository.countBookingsByStatus()
        );
        Map<String, Long> forumReportCounts = toStatusCountMap(
                ForumReportStatus.class,
                adminDashboardQueryRepository.countForumReportsByStatus()
        );
        Map<String, Long> payoutRequestCounts = toStatusCountMap(
                PayoutRequestStatus.class,
                adminDashboardQueryRepository.countPayoutRequestsByStatus()
        );
        Map<String, Long> paymentOrderCounts = toStatusCountMap(
                PaymentOrderStatus.class,
                adminDashboardQueryRepository.countPaymentOrdersByStatus()
        );
        Map<String, Long> emailOutboxCounts = toStatusCountMap(
                NotificationStatus.class,
                adminDashboardQueryRepository.countEmailOutboxByStatus()
        );

        return new AdminDashboardOverviewResponse(
                snapshotAt,
                new AdminDashboardUsersOverviewResponse(
                        userCounts.total(),
                        userCounts.active(),
                        userCounts.banned(),
                        userCounts.deleted(),
                        userCounts.menteeOnly(),
                        userCounts.mentor()
                ),
                new AdminDashboardMentorVerificationOverviewResponse(
                        mentorVerificationCounts.getOrDefault(VerificationStatus.DRAFT.name(), 0L),
                        mentorVerificationCounts.getOrDefault(VerificationStatus.PENDING_REVIEW.name(), 0L),
                        mentorVerificationCounts.getOrDefault(VerificationStatus.NEEDS_REVISION.name(), 0L),
                        mentorVerificationCounts.getOrDefault(VerificationStatus.APPROVED.name(), 0L),
                        mentorVerificationCounts.getOrDefault(VerificationStatus.REJECTED.name(), 0L),
                        mentorVerificationCounts.getOrDefault(VerificationStatus.WITHDRAWN.name(), 0L)
                ),
                bookingCounts,
                forumReportCounts,
                payoutRequestCounts,
                paymentOrderCounts,
                emailOutboxCounts
        );
    }

    public AdminDashboardQueuesResponse getQueues() {
        LocalDateTime snapshotAt = DateTimeUtil.now();
        Map<String, Long> mentorVerificationCounts = adminDashboardQueryRepository.countMentorVerificationByStatus();
        Map<String, Long> bookingCounts = adminDashboardQueryRepository.countBookingsByStatus();
        Map<String, Long> forumReportCounts = adminDashboardQueryRepository.countForumReportsByStatus();
        Map<String, Long> payoutRequestCounts = adminDashboardQueryRepository.countPayoutRequestsByStatus();
        Map<String, Long> paymentOrderCounts = adminDashboardQueryRepository.countPaymentOrdersByStatus();
        Map<String, Long> emailOutboxCounts = adminDashboardQueryRepository.countEmailOutboxByStatus();

        List<AdminDashboardQueueItemResponse> items = List.of(
                new AdminDashboardQueueItemResponse(
                        "mentor_verification_pending_review",
                        "Mentor verification chờ duyệt",
                        mentorVerificationCounts.getOrDefault(VerificationStatus.PENDING_REVIEW.name(), 0L),
                        "high",
                        "/api/admin/mentor-verification/requests?status=PENDING_REVIEW",
                        1
                ),
                new AdminDashboardQueueItemResponse(
                        "booking_under_review",
                        "Booking cần admin review",
                        bookingCounts.getOrDefault(BookingStatus.UNDER_REVIEW.name(), 0L),
                        "high",
                        "/api/admin/bookings?status=UNDER_REVIEW",
                        2
                ),
                new AdminDashboardQueueItemResponse(
                        "forum_reports_open",
                        "Forum report đang mở",
                        forumReportCounts.getOrDefault(ForumReportStatus.OPEN.name(), 0L),
                        "high",
                        "/api/admin/forum/reports?status=OPEN",
                        3
                ),
                new AdminDashboardQueueItemResponse(
                        "payout_requests_requested",
                        "Payout đang chờ duyệt",
                        payoutRequestCounts.getOrDefault(PayoutRequestStatus.REQUESTED.name(), 0L),
                        "medium",
                        "/api/admin/payout-requests?status=REQUESTED",
                        4
                ),
                new AdminDashboardQueueItemResponse(
                        "payment_orders_failed",
                        "Payment thất bại",
                        paymentOrderCounts.getOrDefault(PaymentOrderStatus.FAILED.name(), 0L),
                        "medium",
                        "/api/admin/dashboard/overview",
                        5
                ),
                new AdminDashboardQueueItemResponse(
                        "email_outbox_failed",
                        "Email gửi lỗi",
                        emailOutboxCounts.getOrDefault(NotificationStatus.FAILED.name(), 0L),
                        "medium",
                        "/api/admin/email-outbox?status=FAILED",
                        6
                ),
                new AdminDashboardQueueItemResponse(
                        "bookings_accepted_awaiting_payment",
                        "Booking chờ thanh toán",
                        bookingCounts.getOrDefault(BookingStatus.ACCEPTED_AWAITING_PAYMENT.name(), 0L),
                        "low",
                        "/api/admin/bookings?status=ACCEPTED_AWAITING_PAYMENT",
                        7
                )
        );

        return new AdminDashboardQueuesResponse(snapshotAt, items);
    }

    public AdminDashboardTimeseriesResponse getTimeseries() {
        ZoneId zoneId = ZoneId.of(TIMEZONE);
        LocalDate toDate = LocalDate.now(zoneId);
        LocalDate fromDate = toDate.minusDays(29);
        LocalDateTime fromInclusive = fromDate.atStartOfDay();
        LocalDateTime toExclusive = toDate.plusDays(1).atStartOfDay();

        Map<LocalDate, Long> usersCreated = toDailyCountMap(
                adminDashboardQueryRepository.countUsersCreatedByDay(fromInclusive, toExclusive)
        );
        Map<LocalDate, Long> mentorVerificationSubmitted = toDailyCountMap(
                adminDashboardQueryRepository.countMentorVerificationSubmittedByDay(fromInclusive, toExclusive)
        );
        Map<LocalDate, Long> bookingsCreated = toDailyCountMap(
                adminDashboardQueryRepository.countBookingsCreatedByDay(fromInclusive, toExclusive)
        );
        Map<LocalDate, Long> paymentOrdersPaid = toDailyCountMap(
                adminDashboardQueryRepository.countPaymentOrdersPaidByDay(fromInclusive, toExclusive)
        );
        Map<LocalDate, Long> forumReportsCreated = toDailyCountMap(
                adminDashboardQueryRepository.countForumReportsCreatedByDay(fromInclusive, toExclusive)
        );
        Map<LocalDate, Long> payoutRequestsCreated = toDailyCountMap(
                adminDashboardQueryRepository.countPayoutRequestsCreatedByDay(fromInclusive, toExclusive)
        );

        List<AdminDashboardTimeseriesPointResponse> points = fromDate.datesUntil(toDate.plusDays(1))
                .map(date -> new AdminDashboardTimeseriesPointResponse(
                        date,
                        usersCreated.getOrDefault(date, 0L),
                        mentorVerificationSubmitted.getOrDefault(date, 0L),
                        bookingsCreated.getOrDefault(date, 0L),
                        paymentOrdersPaid.getOrDefault(date, 0L),
                        forumReportsCreated.getOrDefault(date, 0L),
                        payoutRequestsCreated.getOrDefault(date, 0L)
                ))
                .toList();

        return new AdminDashboardTimeseriesResponse(TIMEZONE, fromDate, toDate, points);
    }

    public PageResponse<AdminQueueCaseItemResponse> getQueueItems(UUID adminUserId, AdminQueueCaseListRequest request) {
        return adminQueueWorkbenchService.getQueueItems(adminUserId, request);
    }

    private Map<LocalDate, Long> toDailyCountMap(List<AdminDashboardQueryRepository.DailyCountRow> rows) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (AdminDashboardQueryRepository.DailyCountRow row : rows) {
            counts.put(row.bucketDate(), row.total());
        }
        return counts;
    }

    private <E extends Enum<E>> Map<String, Long> toStatusCountMap(Class<E> enumClass, Map<String, Long> rawCounts) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Set<String> normalizedKeys = new HashSet<>();
        Arrays.stream(enumClass.getEnumConstants()).forEach(status -> {
            counts.put(status.name(), rawCounts.getOrDefault(status.name(), 0L));
            normalizedKeys.add(status.name());
        });
        rawCounts.entrySet().stream()
                .filter(entry -> !normalizedKeys.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }
}

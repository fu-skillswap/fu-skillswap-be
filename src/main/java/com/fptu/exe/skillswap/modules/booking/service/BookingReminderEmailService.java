package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.projection.PendingBookingServiceCountProjection;
import com.fptu.exe.skillswap.modules.notification.service.EmailDispatchService;
import com.fptu.exe.skillswap.modules.notification.template.HtmlEmailTemplate;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingReminderEmailService {

    // Reminder should cover both paid bookings and legacy accepted bookings that are already confirmed for scheduling.
    private static final List<BookingStatus> CONFIRMED_STATUSES = List.of(BookingStatus.PAID);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy");
    private static final DateTimeFormatter DAILY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIGEST_SLOT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAILY_DIGEST_SLOT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PLATFORM_URL = HtmlEmailTemplate.PLATFORM_URL;
    private static final String DEFAULT_SERVICE_TITLE = "Dịch vụ mentoring";

    private final BookingRepository bookingRepository;
    private final EmailDispatchService emailDispatchService;
    private SessionService sessionService;

    @Autowired(required = false)
    void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public int sendUpcomingSessionReminders() {
        LocalDateTime now = DateTimeUtil.now();
        LocalDateTime startInclusive = now.plusMinutes(29);
        LocalDateTime endExclusive = now.plusMinutes(31);
        List<Booking> bookings = bookingRepository.findConfirmedBookingsStartingBetween(
                CONFIRMED_STATUSES,
                startInclusive,
                endExclusive
        );

        int sent = 0;
        for (Booking booking : bookings) {
            if (sendMenteeReminder(booking)) {
                sent++;
            }
            if (sendMentorReminder(booking)) {
                sent++;
            }
        }
        return sent;
    }

    /** Calendar is optional. This warns participants when a confirmed booking has no usable meeting access. */
    public int sendMeetingAccessFallbackWarnings() {
        LocalDateTime now = DateTimeUtil.now();
        int sent = sendMeetingAccessFallbackWarnings(now.plusMinutes(119), now.plusMinutes(121), false);
        return sent + sendMeetingAccessFallbackWarnings(now.plusMinutes(29), now.plusMinutes(31), true);
    }

    private int sendMeetingAccessFallbackWarnings(LocalDateTime startInclusive,
                                                   LocalDateTime endExclusive,
                                                   boolean notifyMentee) {
        List<Booking> bookings = bookingRepository.findConfirmedBookingsStartingBetween(
                CONFIRMED_STATUSES, startInclusive, endExclusive);
        int sent = 0;
        for (Booking booking : bookings) {
            if (hasMeetingAccess(booking)) continue;
            if (sendMissingAccessWarningToMentor(booking, notifyMentee ? "30 phút" : "2 giờ")) sent++;
            if (notifyMentee && sendMissingAccessWarningToMentee(booking)) sent++;
        }
        return sent;
    }

    public int sendPendingRequestDigests() {
        List<PendingBookingServiceCountProjection> rows =
                bookingRepository.countPendingRequestsGroupedByMentorAndService(BookingStatus.PENDING);
        Map<UUID, PendingDigest> digests = new LinkedHashMap<>();
        for (PendingBookingServiceCountProjection row : rows) {
            if (row.getMentorUserId() == null || !hasText(row.getMentorEmail()) || row.getPendingCount() <= 0) {
                continue;
            }
            PendingDigest digest = digests.computeIfAbsent(row.getMentorUserId(), ignored ->
                    new PendingDigest(row.getMentorUserId(), row.getMentorEmail(), defaultText(row.getMentorName(), "mentor")));
            digest.add(defaultText(row.getServiceTitle(), DEFAULT_SERVICE_TITLE), row.getPendingCount());
        }

        String slotKey = DateTimeUtil.now().format(DIGEST_SLOT_FORMATTER);
        int sent = 0;
        for (PendingDigest digest : digests.values()) {
            if (digest.totalCount() <= 0) {
                continue;
            }
            if (sendPendingDigest(digest, slotKey)) {
                sent++;
            }
        }
        return sent;
    }

    public int sendDailyMentorScheduleDigests() {
        LocalDateTime now = DateTimeUtil.now();
        // Check for today's confirmed bookings starting from 02:00 to 23:59:59
        LocalDateTime startInclusive = now.toLocalDate().atTime(2, 0);
        LocalDateTime endExclusive = now.toLocalDate().plusDays(1).atStartOfDay();

        List<Booking> bookings = bookingRepository.findConfirmedBookingsStartingBetween(
                CONFIRMED_STATUSES,
                startInclusive,
                endExclusive
        );

        if (bookings.isEmpty()) {
            return 0;
        }

        // Group by mentor (only for mentors with active email)
        Map<UUID, List<Booking>> bookingsByMentor = bookings.stream()
                .filter(b -> b.getMentorProfile() != null
                        && b.getMentorProfile().getUser() != null
                        && hasText(b.getMentorProfile().getUser().getEmail()))
                .collect(Collectors.groupingBy(
                        b -> b.getMentorProfile().getUserId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        String dateKey = now.format(DAILY_DIGEST_SLOT_FORMATTER);
        String dateDisplay = now.format(DAILY_DATE_FORMATTER);

        int sent = 0;
        for (Map.Entry<UUID, List<Booking>> entry : bookingsByMentor.entrySet()) {
            UUID mentorUserId = entry.getKey();
            List<Booking> mentorBookings = entry.getValue();
            if (mentorBookings.isEmpty()) {
                continue;
            }

            Booking firstBooking = mentorBookings.get(0);
            String mentorEmail = firstBooking.getMentorProfile().getUser().getEmail();
            String mentorName = mentorName(firstBooking);

            if (sendSingleMentorDailyDigest(mentorUserId, mentorEmail, mentorName, mentorBookings, dateKey, dateDisplay)) {
                sent++;
            }

            // Pacing delay (50ms) to throttle and avoid bursting SMTP/API limits
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        return sent;
    }

    public int sendAutoCloseWarningEmails() {
        LocalDateTime now = DateTimeUtil.now();
        // Auto-close is anchored to selectedEndTime + 24 hours; this query targets the one-hour warning.
        LocalDateTime endExclusive = now.plusHours(1);
        LocalDateTime startInclusive = endExclusive.minusMinutes(1);

        int sent = 0;
        for (BookingStatus status : List.of(BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.AWAITING_MENTEE_CONFIRMATION)) {
            for (Booking booking : bookingRepository.findBookingsAboutToAutoClose(status, startInclusive, endExclusive)) {
                if (sendMenteeAutoCloseWarning(booking)) sent++;
                if (sendMentorAutoCloseWarning(booking)) sent++;
            }
        }
        return sent;
    }

    private boolean hasMeetingAccess(Booking booking) {
        if (booking == null) return false;
        Session session = sessionService == null ? null : sessionService.findByBookingId(booking.getId());
        MeetingPlatform platform = session == null ? booking.getMeetingPlatform() : session.getMeetingPlatform();
        String link = session == null ? booking.getMeetingLink() : session.getMeetingLink();
        if (platform == MeetingPlatform.OFFLINE) return hasText(booking.getLocation());
        return hasText(link);
    }

    private boolean sendMissingAccessWarningToMentor(Booking booking, String remainingTime) {
        if (booking == null || booking.getId() == null || booking.getMentorProfile() == null
                || booking.getMentorProfile().getUser() == null || !hasText(booking.getMentorProfile().getUser().getEmail())) return false;
        String subject = "[SkillSwap] Booking chưa có thông tin tham gia buổi học";
        EmailPayload payload = buildSessionReminderPayload(booking, subject, "Cần bổ sung thông tin buổi học",
                "Cần thao tác", mentorName(booking), menteeName(booking),
                "Buổi mentoring sẽ bắt đầu sau " + remainingTime + " nhưng chưa có link họp hoặc địa điểm hợp lệ.",
                "Hãy vào SkillSwap để bổ sung link họp hoặc địa điểm ngay.", "Cập nhật thông tin buổi học");
        return emailDispatchService.sendHtmlOnce("BOOKING_MISSING_ACCESS_MENTOR:" + remainingTime + ":" + booking.getId(),
                booking.getMentorProfile().getUser().getEmail(), payload.subject(), payload.html(), payload.plainText(), "BOOKING_MISSING_ACCESS_MENTOR");
    }

    private boolean sendMissingAccessWarningToMentee(Booking booking) {
        if (booking == null || booking.getId() == null || booking.getMentee() == null || !hasText(booking.getMentee().getEmail())) return false;
        String subject = "[SkillSwap] Buổi học chưa có thông tin tham gia";
        EmailPayload payload = buildSessionReminderPayload(booking, subject, "Cần kiểm tra thông tin buổi học",
                "Cần chú ý", menteeName(booking), mentorName(booking),
                "Buổi mentoring sẽ bắt đầu sau 30 phút nhưng hiện chưa có link họp hoặc địa điểm hợp lệ.",
                "Hãy liên hệ mentor qua SkillSwap; nếu không thể tham gia khi tới giờ, bạn có thể báo mentor no-show.", "Xem booking");
        return emailDispatchService.sendHtmlOnce("BOOKING_MISSING_ACCESS_MENTEE:" + booking.getId(),
                booking.getMentee().getEmail(), payload.subject(), payload.html(), payload.plainText(), "BOOKING_MISSING_ACCESS_MENTEE");
    }

    private boolean sendMenteeAutoCloseWarning(Booking booking) {
        if (booking == null || booking.getId() == null || booking.getMentee() == null || !hasText(booking.getMentee().getEmail())) {
            return false;
        }
        EmailPayload payload = buildSessionReminderPayload(
                booking,
                "[SkillSwap] Sắp tự động đóng buổi học",
                "Buổi học sắp tự động đóng",
                "Đang chờ xác nhận",
                defaultText(booking.getMentee().getFullName(), "bạn"),
                mentorName(booking),
                "Chỉ còn 1 giờ nữa buổi học với " + mentorName(booking) + " sẽ tự động đóng và giải ngân cho mentor.",
                "Vui lòng vào SkillSwap để xác nhận hoàn tất hoặc báo cáo sự cố nếu có.",
                "Đến trang quản lý"
        );
        return emailDispatchService.sendHtmlOnce(
                "BOOKING_AUTO_CLOSE_WARNING_MENTEE:" + booking.getId(),
                booking.getMentee().getEmail(),
                payload.subject(),
                payload.html(),
                payload.plainText(),
                "BOOKING_AUTO_CLOSE_WARNING_MENTEE"
        );
    }

    private boolean sendMentorAutoCloseWarning(Booking booking) {
        if (booking == null || booking.getId() == null || booking.getMentorProfile() == null
                || booking.getMentorProfile().getUser() == null || !hasText(booking.getMentorProfile().getUser().getEmail())) {
            return false;
        }
        EmailPayload payload = buildSessionReminderPayload(
                booking,
                "[SkillSwap] Sắp giải ngân tiền học phí",
                "Sắp giải ngân tiền học phí",
                "Đang chờ xác nhận",
                mentorName(booking),
                menteeName(booking),
                "Chỉ còn 1 giờ nữa buổi học với " + menteeName(booking) + " sẽ tự động đóng. Nếu không có khiếu nại, tiền sẽ được chuyển vào ví của bạn.",
                "Theo dõi trạng thái buổi mentoring trên hệ thống.",
                "Đến trang quản lý"
        );
        return emailDispatchService.sendHtmlOnce(
                "BOOKING_AUTO_CLOSE_WARNING_MENTOR:" + booking.getId(),
                booking.getMentorProfile().getUser().getEmail(),
                payload.subject(),
                payload.html(),
                payload.plainText(),
                "BOOKING_AUTO_CLOSE_WARNING_MENTOR"
        );
    }

    private boolean sendMenteeReminder(Booking booking) {
        if (booking == null || booking.getId() == null || booking.getMentee() == null || !hasText(booking.getMentee().getEmail())) {
            return false;
        }
        EmailPayload payload = buildSessionReminderPayload(
                booking,
                "[SkillSwap] Buổi học của bạn bắt đầu sau 30 phút",
                "Buổi học của bạn bắt đầu sau 30 phút",
                "Lịch học",
                defaultText(booking.getMentee().getFullName(), "bạn"),
                mentorName(booking),
                "Buổi học của bạn với " + mentorName(booking) + " sẽ bắt đầu sau khoảng 30 phút.",
                "Chuẩn bị nội dung cần hỏi và truy cập SkillSwap đúng giờ để vào buổi học.",
                "Xem lịch học"
        );
        return emailDispatchService.sendHtmlOnce(
                "BOOKING_SESSION_REMINDER_MENTEE:" + booking.getId(),
                booking.getMentee().getEmail(),
                payload.subject(),
                payload.html(),
                payload.plainText(),
                "BOOKING_SESSION_REMINDER_MENTEE"
        );
    }

    private boolean sendMentorReminder(Booking booking) {
        if (booking == null || booking.getId() == null || booking.getMentorProfile() == null
                || booking.getMentorProfile().getUser() == null || !hasText(booking.getMentorProfile().getUser().getEmail())) {
            return false;
        }
        EmailPayload payload = buildSessionReminderPayload(
                booking,
                "[SkillSwap] Buổi mentoring bắt đầu sau 30 phút",
                "Buổi mentoring bắt đầu sau 30 phút",
                "Mentoring",
                mentorName(booking),
                menteeName(booking),
                "Buổi mentoring với " + menteeName(booking) + " sẽ bắt đầu sau khoảng 30 phút.",
                "Kiểm tra mục tiêu của mentee, chuẩn bị link học hoặc không gian mentoring trước giờ bắt đầu.",
                "Xem lịch mentoring"
        );
        return emailDispatchService.sendHtmlOnce(
                "BOOKING_SESSION_REMINDER_MENTOR:" + booking.getId(),
                booking.getMentorProfile().getUser().getEmail(),
                payload.subject(),
                payload.html(),
                payload.plainText(),
                "BOOKING_SESSION_REMINDER_MENTOR"
        );
    }

    private boolean sendPendingDigest(PendingDigest digest, String slotKey) {
        String subject = "[SkillSwap] Bạn có " + digest.totalCount() + " yêu cầu mentoring đang chờ xác nhận";
        String detailRows = digest.serviceCounts().entrySet().stream()
                .map(entry -> HtmlEmailTemplate.detailRow(
                        entry.getKey(),
                        HtmlEmailTemplate.escape(entry.getValue() + " yêu cầu")
                ))
                .reduce("", String::concat);
        String intro = "Bạn đang có " + digest.totalCount() + " yêu cầu mentoring đang chờ phản hồi trên SkillSwap.";
        String html = HtmlEmailTemplate.render(new HtmlEmailTemplate.Model(
                subject,
                intro,
                "Cần xác nhận",
                "Bạn có yêu cầu mentoring đang chờ",
                "Hệ thống tổng hợp các request pending theo từng service để bạn xử lý nhanh hơn.",
                "Mentor",
                "Pending requests",
                digest.mentorName(),
                intro,
                detailRows,
                "Vui lòng vào SkillSwap để xác nhận hoặc từ chối các yêu cầu phù hợp với lịch của bạn.",
                "Xem yêu cầu mentoring",
                PLATFORM_URL
        ));
        String plainText = subject + "\n\nXin chào " + digest.mentorName() + ",\n\n"
                + intro + "\n\n"
                + digest.serviceCounts().entrySet().stream()
                .map(entry -> "- " + entry.getKey() + ": " + entry.getValue() + " yêu cầu")
                .reduce("", (left, right) -> left + right + "\n")
                + "\nTruy cập SkillSwap: " + PLATFORM_URL + "\n";
        return emailDispatchService.sendHtmlOnce(
                "MENTOR_PENDING_REQUEST_DIGEST:" + digest.mentorUserId() + ":" + slotKey,
                digest.mentorEmail(),
                subject,
                html,
                plainText,
                "MENTOR_PENDING_REQUEST_DIGEST"
        );
    }

    private boolean sendSingleMentorDailyDigest(
            UUID mentorUserId,
            String mentorEmail,
            String mentorName,
            List<Booking> mentorBookings,
            String dateKey,
            String dateDisplay
    ) {
        int sessionCount = mentorBookings.size();
        String subject = "[SkillSwap] Lịch mentoring của bạn hôm nay (" + dateDisplay + ") - " + sessionCount + " buổi học";
        String intro = "Hôm nay (" + dateDisplay + "), bạn có " + sessionCount + " buổi mentoring đã được xác nhận trên SkillSwap.";

        StringBuilder detailRowsBuilder = new StringBuilder();
        StringBuilder plainTextSchedule = new StringBuilder();

        for (int i = 0; i < mentorBookings.size(); i++) {
            Booking b = mentorBookings.get(i);
            String timeStr = formatTimeRange(b.getSelectedStartTime(), b.getSelectedEndTime());
            String mentee = menteeName(b);
            String sTitle = serviceTitle(b);
            String goal = defaultText(b.getLearningGoalTitle(), "Mentoring session");
            String meet = hasText(b.getMeetingLink()) ? b.getMeetingLink() : "";

            String sessionSummary = "Ca " + (i + 1) + " (" + timeStr + ") | Học viên: " + mentee;
            String sessionDetails = "Dịch vụ: " + sTitle + " - Mục tiêu: " + goal + (hasText(meet) ? " - Link: " + meet : "");

            detailRowsBuilder.append(HtmlEmailTemplate.detailRow(sessionSummary, HtmlEmailTemplate.escape(sessionDetails)));

            plainTextSchedule.append("- Ca ").append(i + 1).append(": ").append(timeStr)
                    .append(" | Học viên: ").append(mentee)
                    .append(" | Dịch vụ: ").append(sTitle)
                    .append(hasText(meet) ? " | Link: " + meet : "")
                    .append("\n");
        }

        String html = HtmlEmailTemplate.render(new HtmlEmailTemplate.Model(
                subject,
                intro,
                "Lịch hôm nay",
                "Tổng hợp lịch dạy hôm nay",
                "Hệ thống tổng hợp toàn bộ các ca mentoring trong ngày để bạn dễ dàng sắp xếp thời gian.",
                "SkillSwap",
                "Daily Schedule",
                mentorName,
                intro,
                detailRowsBuilder.toString(),
                "Vui lòng kiểm tra lịch học, chuẩn bị tài liệu và tham gia phòng học đúng giờ.",
                "Xem lịch mentoring",
                PLATFORM_URL
        ));

        String plainText = "[SkillSwap] Lịch mentoring hôm nay (" + dateDisplay + ")\n\n"
                + "Xin chào " + mentorName + ",\n\n"
                + intro + "\n\n"
                + plainTextSchedule.toString() + "\n"
                + "Vui lòng chuẩn bị và tham gia đúng giờ.\n"
                + "Truy cập SkillSwap: " + PLATFORM_URL + "\n";

        return emailDispatchService.sendHtmlOnce(
                "MENTOR_DAILY_SCHEDULE_DIGEST:" + mentorUserId + ":" + dateKey,
                mentorEmail,
                subject,
                html,
                plainText,
                "MENTOR_DAILY_SCHEDULE_DIGEST"
        );
    }

    private String formatTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return "Chưa xác định";
        return start.format(TIME_FORMATTER) + " - " + end.format(TIME_FORMATTER);
    }

    private EmailPayload buildSessionReminderPayload(
            Booking booking,
            String subject,
            String heading,
            String statusLabel,
            String recipientName,
            String relatedPersonName,
            String intro,
            String nextStep,
            String ctaLabel
    ) {
        String serviceTitle = serviceTitle(booking);
        String detailRows = HtmlEmailTemplate.detailRow("Mã booking", HtmlEmailTemplate.escape(shortBookingId(booking)))
                + HtmlEmailTemplate.detailRow("Dịch vụ", HtmlEmailTemplate.escape(serviceTitle))
                + HtmlEmailTemplate.detailRow("Người liên quan", HtmlEmailTemplate.escape(relatedPersonName))
                + HtmlEmailTemplate.detailRow("Thời gian", HtmlEmailTemplate.escape(formatSchedule(booking)))
                + HtmlEmailTemplate.detailRow("Thời lượng", HtmlEmailTemplate.escape(formatDuration(booking)))
                + HtmlEmailTemplate.detailRow("Mục tiêu", HtmlEmailTemplate.escape(defaultText(booking.getLearningGoalTitle(), "Mục tiêu mentoring")))
                + (hasText(booking.getLearningGoalDescription())
                ? HtmlEmailTemplate.detailRow("Mô tả", HtmlEmailTemplate.escape(booking.getLearningGoalDescription()))
                : "")
                + (hasText(booking.getMeetingLink())
                ? HtmlEmailTemplate.detailRow("Link học", HtmlEmailTemplate.safeLink(booking.getMeetingLink()))
                : "");

        String html = HtmlEmailTemplate.render(new HtmlEmailTemplate.Model(
                subject,
                intro,
                statusLabel,
                heading,
                intro,
                "SkillSwap",
                serviceTitle,
                recipientName,
                intro,
                detailRows,
                nextStep,
                ctaLabel,
                PLATFORM_URL
        ));
        String plainText = heading + "\n\nXin chào " + recipientName + ",\n\n"
                + intro + "\n\n"
                + "Mã booking: " + shortBookingId(booking) + "\n"
                + "Dịch vụ: " + serviceTitle + "\n"
                + "Người liên quan: " + relatedPersonName + "\n"
                + "Thời gian: " + formatSchedule(booking) + "\n"
                + "Thời lượng: " + formatDuration(booking) + "\n"
                + "Mục tiêu: " + defaultText(booking.getLearningGoalTitle(), "Mục tiêu mentoring") + "\n"
                + (hasText(booking.getMeetingLink()) ? "Link học: " + booking.getMeetingLink() + "\n" : "")
                + "\nBước tiếp theo: " + nextStep + "\n"
                + "Truy cập SkillSwap: " + PLATFORM_URL + "\n";
        return new EmailPayload(subject, html, plainText);
    }

    private String formatSchedule(Booking booking) {
        if (booking.getSelectedStartTime() == null || booking.getSelectedEndTime() == null) {
            return "Chưa xác định";
        }
        return formatDateTime(booking.getSelectedStartTime()) + " - " + formatDateTime(booking.getSelectedEndTime());
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "Chưa xác định" : value.format(DATE_TIME_FORMATTER);
    }

    private String formatDuration(Booking booking) {
        if (booking.getSelectedStartTime() != null && booking.getSelectedEndTime() != null) {
            long minutes = Duration.between(booking.getSelectedStartTime(), booking.getSelectedEndTime()).toMinutes();
            if (minutes > 0) {
                return minutes + " phút";
            }
        }
        Integer snapshot = booking.getServiceDurationSnapshot();
        return snapshot == null || snapshot <= 0 ? "Theo lịch đã đặt" : snapshot + " phút";
    }

    private String shortBookingId(Booking booking) {
        if (booking.getId() == null) {
            return "N/A";
        }
        String value = booking.getId().toString();
        return value.length() <= 8 ? value : value.substring(0, 8).toUpperCase();
    }

    private String serviceTitle(Booking booking) {
        if (hasText(booking.getServiceTitleSnapshot())) {
            return booking.getServiceTitleSnapshot().trim();
        }
        if (booking.getService() != null && hasText(booking.getService().getTitle())) {
            return booking.getService().getTitle().trim();
        }
        return DEFAULT_SERVICE_TITLE;
    }

    private String mentorName(Booking booking) {
        if (booking.getMentorProfile() != null && booking.getMentorProfile().getUser() != null) {
            return defaultText(booking.getMentorProfile().getUser().getFullName(), "mentor");
        }
        return "mentor";
    }

    private String menteeName(Booking booking) {
        if (booking.getMentee() != null) {
            return defaultText(booking.getMentee().getFullName(), "mentee");
        }
        return "mentee";
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record EmailPayload(String subject, String html, String plainText) {
    }

    private record PendingDigest(
            UUID mentorUserId,
            String mentorEmail,
            String mentorName,
            Map<String, Long> serviceCounts
    ) {
        private PendingDigest(UUID mentorUserId, String mentorEmail, String mentorName) {
            this(mentorUserId, mentorEmail, mentorName, new LinkedHashMap<>());
        }

        private void add(String serviceTitle, long count) {
            serviceCounts.merge(serviceTitle, count, Long::sum);
        }

        private long totalCount() {
            return serviceCounts.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}

package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.service.meeting.MeetingProviderFactory;
import com.fptu.exe.skillswap.modules.booking.event.BookingCalendarLifecycleEvent;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.NotificationEvent;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.fptu.exe.skillswap.modules.booking.service.BookingResponseMapper.isScheduledBookingStatus;

@Service
@RequiredArgsConstructor
public class BookingMeetingService {

    private final BookingRepository bookingRepository;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;
    private final MeetingProviderFactory meetingProviderFactory;
    private final BookingResponseMapper bookingResponseMapper;
    private final com.fptu.exe.skillswap.modules.identity.port.UserQueryPort userQueryPort;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional
    public BookingResponse saveMeetingLink(UUID mentorUserId, UUID bookingId, SaveMeetingLinkRequest request) {
        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu meeting link");
        }
        if (!isScheduledBookingStatus(booking.getStatus()) && booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể cập nhật meeting link cho booking đã được xác nhận hoặc chờ thanh toán");
        }

        Instant startUtc = BookingTime.resolveSelectedStartUtc(booking);
        if (startUtc != null && !startUtc.isAfter(timeProvider.instant())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể cập nhật thông tin phòng học sau khi buổi học đã bắt đầu");
        }

        Session session = sessionService.findByBookingId(bookingId);
        MeetingPlatform previousPlatform = session != null ? session.getMeetingPlatform() : booking.getMeetingPlatform();
        String previousMeetingLink = trimToNull(session != null ? session.getMeetingLink() : booking.getMeetingLink());
        String previousLocation = trimToNull(booking.getLocation());
        MeetingPlatform nextPlatform = request.meetingPlatform();
        String nextMeetingLink = cleanMeetingLink(nextPlatform, request.meetingLink());
        String nextLocation = trimToNull(request.location());

        if (nextPlatform == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Vui lòng chọn nền tảng phòng học");
        }
        if (nextPlatform == MeetingPlatform.OFFLINE && !StringUtils.hasText(nextLocation) && !StringUtils.hasText(nextMeetingLink)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Địa điểm hoặc thông tin gặp mặt là bắt buộc đối với hình thức Offline");
        }
        if (nextPlatform != MeetingPlatform.OFFLINE && !StringUtils.hasText(nextMeetingLink)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Link phòng học là bắt buộc");
        }

        boolean meetingChanged = !Objects.equals(previousPlatform, nextPlatform)
                || !Objects.equals(previousMeetingLink, nextMeetingLink)
                || !Objects.equals(previousLocation, nextLocation);
        if (session != null && session.isGoogleCalendarManaged() && meetingChanged) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Thông tin cuộc họp đang được Google Calendar quản lý, không thể sửa thủ công");
        }

        if (session != null) {
            session.setMeetingPlatform(nextPlatform);
            session.setMeetingLink(nextMeetingLink);
            session.setGoogleCalendarManaged(false);
            sessionService.save(session);
        }
        booking.setMeetingPlatform(nextPlatform);
        booking.setMeetingLink(nextMeetingLink);
        booking.setLocation(nextLocation);

        Booking savedBooking = bookingRepository.save(booking);

        if (meetingChanged) {
            String mentorName = savedBooking.getMentorUserId() != null && userQueryPort != null
                    ? userQueryPort.findUserSummaryById(savedBooking.getMentorUserId()).map(com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord::fullName).orElse("Mentor")
                    : "Mentor";
            eventPublisher.publishEvent(new NotificationEvent(
                    savedBooking.getMenteeUserId(),
                    NotificationType.MEETING_LINK_UPDATED,
                    "Thông tin buổi học đã được cập nhật",
                    mentorName + " đã cập nhật link hoặc địa điểm học.",
                    "BOOKING",
                    savedBooking.getId()
            ));
            eventPublisher.publishEvent(BookingCalendarLifecycleEvent.of(
                    savedBooking.getId(), savedBooking.getMentorUserId(), BookingCalendarLifecycleEvent.Action.UPDATE));
        }

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMenteeUserId(),
                savedBooking.getMentorUserId(),
                savedBooking.getStatus(),
                "Thông tin phòng học đã được cập nhật.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : timeProvider.nowBusiness()
        ));

        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    private Booking getBookingForMentorDecision(UUID mentorUserId, UUID bookingId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không được để trống");
        }
        Booking booking = bookingRepository.findByIdForMentorDecision(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        if (booking.getMentorUserId() == null || !mentorUserId.equals(booking.getMentorUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền thao tác trên booking này");
        }
        return booking;
    }

    private String cleanMeetingLink(MeetingPlatform platform, String rawLink) {
        String trimmed = trimToNull(rawLink);
        if (trimmed == null) {
            return null;
        }
        if (meetingProviderFactory != null && platform != null) {
            meetingProviderFactory.getProvider(platform).validateMeetingLink(trimmed);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

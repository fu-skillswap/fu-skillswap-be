package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.service.meeting.MeetingProviderFactory;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public BookingResponse saveMeetingLink(UUID mentorUserId, UUID bookingId, SaveMeetingLinkRequest request) {
        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu meeting link");
        }
        if (!isScheduledBookingStatus(booking.getStatus())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể cập nhật meeting link cho booking đã được xác nhận thanh toán");
        }

        Session session = sessionService.findByBookingId(bookingId);
        if (session == null) {
            session = sessionService.createForAcceptedBooking(booking);
        }
        MeetingPlatform previousPlatform = session.getMeetingPlatform();
        String previousMeetingLink = trimToNull(session.getMeetingLink());
        String previousLocation = trimToNull(booking.getLocation());
        MeetingPlatform nextPlatform = request.meetingPlatform();
        String nextMeetingLink = cleanMeetingLink(nextPlatform, request.meetingLink());
        String nextLocation = trimToNull(request.location());
        boolean meetingChanged = !Objects.equals(previousPlatform, nextPlatform)
                || !Objects.equals(previousMeetingLink, nextMeetingLink)
                || !Objects.equals(previousLocation, nextLocation);
        if (session.isGoogleCalendarManaged() && meetingChanged) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Thông tin cuộc họp đang được Google Calendar quản lý, không thể sửa thủ công");
        }

        session.setMeetingPlatform(nextPlatform);
        session.setMeetingLink(nextMeetingLink);
        booking.setLocation(nextLocation);

        Booking savedBooking = bookingRepository.save(booking);

        if (meetingChanged) {
            eventPublisher.publishEvent(new NotificationEvent(
                    savedBooking.getMentee().getId(),
                    NotificationType.MEETING_LINK_UPDATED,
                    "Thông tin buổi học đã được cập nhật",
                    savedBooking.getMentorProfile().getUser().getFullName() + " đã cập nhật link hoặc địa điểm học.",
                    "BOOKING",
                    savedBooking.getId()
            ));
        }

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Thông tin phòng học đã được cập nhật.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
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
        if (booking.getMentorProfile() == null || !mentorUserId.equals(booking.getMentorProfile().getUserId())) {
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

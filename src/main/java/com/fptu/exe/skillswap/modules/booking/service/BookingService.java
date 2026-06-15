package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.dto.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.BookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.BookingViewRole;
import com.fptu.exe.skillswap.modules.booking.dto.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    @Transactional
    public BookingResponse createBooking(UUID menteeUserId, CreateBookingRequest request) {
        if (menteeUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu tạo booking");
        }

        User mentee = userRepository.findById(menteeUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng hiện tại"));
        if (mentee.getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(ErrorCode.USER_INACTIVE, "Tài khoản hiện tại không ở trạng thái có thể tạo booking");
        }

        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.findByIdForUpdate(request.availabilitySlotId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ mentoring"));

        MentorProfile mentorProfile = slot.getMentorProfile();
        if (mentorProfile == null || mentorProfile.getUser() == null || mentorProfile.getUserId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ hiện tại không gắn với mentor hợp lệ");
        }
        if (!mentorProfile.getUserId().equals(request.mentorUserId())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ này không thuộc mentor đã chọn");
        }
        if (mentorProfile.getUserId().equals(menteeUserId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn không thể tự tạo booking với chính mình");
        }
        if (mentorProfile.getStatus() != MentorStatus.ACTIVE || mentorProfile.getVerifiedAt() == null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng nhận booking");
        }
        if (!mentorProfile.isAvailable()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện đang tạm dừng nhận mentee mới");
        }
        if (!slot.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này hiện không còn khả dụng");
        }
        if (slot.isBooked()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này vừa được mentee khác đặt, vui lòng chọn slot khác");
        }
        if (slot.getStartTime() == null || slot.getEndTime() == null || !slot.getEndTime().isAfter(slot.getStartTime())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ mentoring hiện tại không hợp lệ");
        }
        if (!slot.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này đã bắt đầu hoặc đã trôi qua");
        }

        MentorService mentorService = resolveMentorService(request.serviceId(), mentorProfile.getUserId());

        slot.setBooked(true);

        Booking savedBooking = bookingRepository.save(Booking.builder()
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .service(mentorService)
                .slot(slot)
                .learningGoalTitle(trim(request.learningGoalTitle()))
                .learningGoalDescription(trimToNull(request.learningGoalDescription()))
                .requestedStartTime(slot.getStartTime())
                .requestedEndTime(slot.getEndTime())
                .build());

        return BookingResponse.builder()
                .bookingId(savedBooking.getId())
                .sessionId(null)
                .sessionStatus(null)
                .mentorUserId(mentorProfile.getUserId())
                .mentorDisplayName(mentorProfile.getUser().getFullName())
                .mentorAvatarUrl(mentorProfile.getUser().getAvatarUrl())
                .menteeUserId(mentee.getId())
                .menteeDisplayName(mentee.getFullName())
                .menteeAvatarUrl(mentee.getAvatarUrl())
                .slotId(slot.getId())
                .serviceId(mentorService == null ? null : mentorService.getId())
                .serviceTitle(mentorService == null ? null : mentorService.getTitle())
                .status(savedBooking.getStatus())
                .learningGoalTitle(savedBooking.getLearningGoalTitle())
                .learningGoalDescription(savedBooking.getLearningGoalDescription())
                .mentorResponseNote(savedBooking.getMentorResponseNote())
                .rejectReason(savedBooking.getRejectReason())
                .requestedStartTime(savedBooking.getRequestedStartTime())
                .requestedEndTime(savedBooking.getRequestedEndTime())
                .acceptedAt(savedBooking.getAcceptedAt())
                .rejectedAt(savedBooking.getRejectedAt())
                .createdAt(savedBooking.getCreatedAt())
                .updatedAt(savedBooking.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMyBookings(UUID currentUserId, BookingListRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        BookingListRequest safeRequest = request == null ? new BookingListRequest() : request;
        BookingViewRole role = safeRequest.getRole() == null ? BookingViewRole.MENTEE : safeRequest.getRole();
        Pageable pageable = bookingPageable(safeRequest);

        Page<Booking> page = switch (role) {
            case MENTEE -> safeRequest.getStatus() == null
                    ? bookingRepository.findByMenteeId(currentUserId, pageable)
                    : bookingRepository.findByMenteeIdAndStatus(currentUserId, safeRequest.getStatus(), pageable);
            case MENTOR -> safeRequest.getStatus() == null
                    ? bookingRepository.findByMentorProfileUserId(currentUserId, pageable)
                    : bookingRepository.findByMentorProfileUserIdAndStatus(currentUserId, safeRequest.getStatus(), pageable);
        };

        Map<UUID, Session> sessionsByBookingId = loadSessionsByBookingId(page);

        return PageResponse.<BookingResponse>builder()
                .content(page.getContent().stream()
                        .map(booking -> toBookingResponse(booking, sessionsByBookingId.get(booking.getId())))
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingDetail(UUID currentUserId, UUID bookingId) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        assertBookingAccess(booking, currentUserId);

        Session session = sessionRepository.findByBookingId(bookingId).orElse(null);
        return toBookingResponse(booking, session);
    }

    @Transactional
    public BookingResponse acceptBooking(UUID mentorUserId, UUID bookingId, AcceptBookingRequest request) {
        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể chấp nhận booking đang chờ phản hồi");
        }

        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setAcceptedAt(LocalDateTime.now());
        booking.setRejectedAt(null);
        booking.setRejectReason(null);
        booking.setMentorResponseNote(trimToNull(request == null ? null : request.mentorResponseNote()));

        Session session = sessionRepository.findByBookingId(bookingId)
                .orElseGet(() -> sessionRepository.save(Session.builder().booking(booking).build()));

        return toBookingResponse(booking, session);
    }

    @Transactional
    public BookingResponse rejectBooking(UUID mentorUserId, UUID bookingId, RejectBookingRequest request) {
        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể từ chối booking đang chờ phản hồi");
        }

        booking.setStatus(BookingStatus.REJECTED);
        booking.setRejectedAt(LocalDateTime.now());
        booking.setAcceptedAt(null);
        booking.setRejectReason(trim(request.rejectReason()));
        booking.setMentorResponseNote(trimToNull(request.mentorResponseNote()));

        MentorAvailabilitySlot slot = booking.getSlot();
        if (slot != null) {
            slot.setBooked(false);
        }
        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            mentorProfile.setTotalRejectedBookings(defaultInteger(mentorProfile.getTotalRejectedBookings()) + 1);
        }

        return toBookingResponse(booking, null);
    }

    private MentorService resolveMentorService(UUID serviceId, UUID mentorUserId) {
        if (serviceId == null) {
            return null;
        }

        MentorService mentorService = mentorServiceRepository.findByIdAndMentorProfileUserIdAndIsActiveTrue(serviceId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring đã chọn không tồn tại hoặc không thuộc mentor này"));
        if (mentorService.getDurationMinutes() == null || mentorService.getDurationMinutes() <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring đã chọn có thời lượng không hợp lệ");
        }
        return mentorService;
    }

    private Booking getBookingForMentorDecision(UUID mentorUserId, UUID bookingId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        Booking booking = bookingRepository.findByIdForMentorDecision(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        if (!booking.getMentorProfile().getUserId().equals(mentorUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xử lý booking này");
        }
        return booking;
    }

    private void assertBookingAccess(Booking booking, UUID currentUserId) {
        boolean isMentee = booking.getMentee() != null && currentUserId.equals(booking.getMentee().getId());
        boolean isMentor = booking.getMentorProfile() != null && currentUserId.equals(booking.getMentorProfile().getUserId());
        if (!isMentee && !isMentor) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem booking này");
        }
    }

    private Map<UUID, Session> loadSessionsByBookingId(Page<Booking> page) {
        List<UUID> bookingIds = page.getContent().stream()
                .map(Booking::getId)
                .distinct()
                .toList();
        if (bookingIds.isEmpty()) {
            return Map.of();
        }

        return sessionRepository.findByBookingIdIn(bookingIds).stream()
                .filter(session -> session.getBooking() != null && session.getBooking().getId() != null)
                .collect(Collectors.toMap(session -> session.getBooking().getId(), Function.identity()));
    }

    private BookingResponse toBookingResponse(Booking booking, Session session) {
        User mentee = booking.getMentee();
        MentorProfile mentorProfile = booking.getMentorProfile();
        User mentorUser = mentorProfile == null ? null : mentorProfile.getUser();
        MentorService mentorService = booking.getService();
        MentorAvailabilitySlot slot = booking.getSlot();

        return BookingResponse.builder()
                .bookingId(booking.getId())
                .sessionId(session == null ? null : session.getId())
                .sessionStatus(session == null ? null : session.getStatus())
                .mentorUserId(mentorProfile == null ? null : mentorProfile.getUserId())
                .mentorDisplayName(mentorUser == null ? null : mentorUser.getFullName())
                .mentorAvatarUrl(mentorUser == null ? null : mentorUser.getAvatarUrl())
                .menteeUserId(mentee == null ? null : mentee.getId())
                .menteeDisplayName(mentee == null ? null : mentee.getFullName())
                .menteeAvatarUrl(mentee == null ? null : mentee.getAvatarUrl())
                .slotId(slot == null ? null : slot.getId())
                .serviceId(mentorService == null ? null : mentorService.getId())
                .serviceTitle(mentorService == null ? null : mentorService.getTitle())
                .status(booking.getStatus())
                .learningGoalTitle(booking.getLearningGoalTitle())
                .learningGoalDescription(booking.getLearningGoalDescription())
                .mentorResponseNote(booking.getMentorResponseNote())
                .rejectReason(booking.getRejectReason())
                .requestedStartTime(booking.getRequestedStartTime())
                .requestedEndTime(booking.getRequestedEndTime())
                .acceptedAt(booking.getAcceptedAt())
                .rejectedAt(booking.getRejectedAt())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    private Pageable bookingPageable(BookingListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 20);
        Sort.Direction direction = request.resolveDirection();
        String sortBy = request.getSortBy() == null || request.getSortBy().isBlank()
                ? "requestedStartTime"
                : request.getSortBy().trim();

        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }
}

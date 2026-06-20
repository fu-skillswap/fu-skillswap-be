package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.BookingViewRole;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
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

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final long MENTEE_ACCEPTED_CANCEL_RELEASE_DEADLINE_MINUTES = 8 * 60;
    private static final long MENTOR_SAFE_CANCEL_DEADLINE_MINUTES = 12 * 60;
    private static final long MENTOR_SUSPENSION_CANCEL_DEADLINE_MINUTES = 6 * 60;
    private static final long MAX_PENDING_REQUESTS_PER_SLOT = 3;
    private static final BigDecimal MENTOR_LATE_CANCEL_PENALTY = BigDecimal.valueOf(0.5);
    private static final int MENTOR_LATE_CANCEL_SUSPENSION_DAYS = 3;
    private static final String AUTO_REJECTED_SLOT_TAKEN_REASON = "Slot was accepted for another booking.";

    private final BookingRepository bookingRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final UserRepository userRepository;
    private final AcademicService academicService;

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
        validateBookerEligibility(mentee);

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
        if (mentorProfile.getUser() == null || mentorProfile.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện không còn hoạt động");
        }
        if (mentorProfile.getStatus() != MentorStatus.ACTIVE || mentorProfile.getVerifiedAt() == null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng nhận booking");
        }
        if (!mentorProfile.isAvailable()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện đang tạm dừng nhận mentee mới");
        }
        if (!isDiscoverableMentorForBooking(mentorProfile)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng hiển thị để nhận booking");
        }
        LocalDateTime now = DateTimeUtil.now();
        if (mentorProfile.getBookingSuspendedUntil() != null && mentorProfile.getBookingSuspendedUntil().isAfter(now)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor đang bị tạm khóa nhận lịch mới đến " + mentorProfile.getBookingSuspendedUntil());
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
        if (!slot.getStartTime().isAfter(now)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này đã bắt đầu hoặc đã trôi qua");
        }
        if (bookingRepository.existsBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này đã được chấp nhận cho một booking khác");
        }
        long pendingBookings = bookingRepository.countBySlotIdAndStatus(slot.getId(), BookingStatus.PENDING);
        if (pendingBookings >= MAX_PENDING_REQUESTS_PER_SLOT) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này đã đạt tối đa 3 yêu cầu chờ xác nhận");
        }
        if (bookingRepository.existsByMenteeIdAndSlotIdAndStatusIn(
                menteeUserId,
                slot.getId(),
                List.of(BookingStatus.PENDING, BookingStatus.ACCEPTED)
        )) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đã có yêu cầu booking đang chờ hoặc đã được chấp nhận cho khung giờ này");
        }

        MentorService mentorService = resolveMentorService(request.serviceId(), mentorProfile.getUserId());

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

        return toBookingResponse(savedBooking);
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

        return PageResponse.<BookingResponse>builder()
                .content(page.getContent().stream()
                        .map(this::toBookingResponse)
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

        return toBookingResponse(booking);
    }

    @Transactional
    public BookingResponse acceptBooking(UUID mentorUserId, UUID bookingId, AcceptBookingRequest request) {
        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể chấp nhận booking đang chờ phản hồi");
        }
        MentorAvailabilitySlot slot = requireLockedSlotForDecision(booking);
        if (!slot.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này hiện không còn khả dụng");
        }
        if (slot.isBooked() || bookingRepository.existsBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này đã được chấp nhận cho booking khác");
        }

        LocalDateTime now = DateTimeUtil.now();
        List<Booking> pendingBookings = bookingRepository.findBySlotIdAndStatusForUpdate(slot.getId(), BookingStatus.PENDING);

        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setAcceptedAt(now);
        booking.setMentorResponseNote(trimToNull(request == null ? null : request.mentorResponseNote()));
        slot.setBooked(true);

        for (Booking pendingBooking : pendingBookings) {
            if (pendingBooking.getId().equals(booking.getId())) {
                continue;
            }
            pendingBooking.setStatus(BookingStatus.REJECTED);
            pendingBooking.setRejectedAt(now);
            pendingBooking.setRejectReason(AUTO_REJECTED_SLOT_TAKEN_REASON);
        }

        bookingRepository.saveAll(pendingBookings);
        Booking savedBooking = bookingRepository.save(booking);
        return toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse rejectBooking(UUID mentorUserId, UUID bookingId, RejectBookingRequest request) {
        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể từ chối booking đang chờ phản hồi");
        }

        booking.setStatus(BookingStatus.REJECTED);
        booking.setRejectedAt(DateTimeUtil.now());
        booking.setRejectReason(trim(request.rejectReason()));
        booking.setMentorResponseNote(trimToNull(request.mentorResponseNote()));

        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            mentorProfile.setTotalRejectedBookings(defaultInteger(mentorProfile.getTotalRejectedBookings()) + 1);
        }

        Booking savedBooking = bookingRepository.save(booking);
        return toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse saveMeetingLink(UUID mentorUserId, UUID bookingId, SaveMeetingLinkRequest request) {
        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu meeting link");
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể cập nhật meeting link cho booking đã được chấp nhận");
        }

        booking.setMeetingPlatform(request.meetingPlatform());
        booking.setMeetingLink(cleanMeetingLink(request.meetingLink()));
        booking.setLocation(trimToNull(request.location()));

        return toBookingResponse(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse completeBooking(UUID currentUserId, UUID bookingId, CompleteBookingRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        assertBookingAccess(booking, currentUserId);

        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể hoàn tất booking đã được chấp nhận");
        }
        LocalDateTime now = DateTimeUtil.now();
        if (booking.getRequestedStartTime() == null || booking.getRequestedEndTime() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian booking không hợp lệ");
        }
        if (now.isBefore(booking.getRequestedStartTime())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chưa thể hoàn tất booking trước thời gian bắt đầu");
        }

        String completionNote = trimToNull(request == null ? null : request.completionNote());
        if (isMentorOfBooking(booking, currentUserId)) {
            booking.setMentorNote(completionNote);
        } else {
            booking.setMenteeNote(completionNote);
        }

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(now);
        if (booking.getActualStartTime() == null) {
            booking.setActualStartTime(booking.getRequestedStartTime());
        }
        if (booking.getActualEndTime() == null) {
            booking.setActualEndTime(booking.getRequestedEndTime());
        }

        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            mentorProfile.setTotalCompletedSessions(defaultInteger(mentorProfile.getTotalCompletedSessions()) + 1);
            mentorProfile.setTotalSessions(defaultInteger(mentorProfile.getTotalSessions()) + 1);
        }

        return toBookingResponse(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getAdminBookings(AdminBookingListRequest request) {
        AdminBookingListRequest safeRequest = request == null ? new AdminBookingListRequest() : request;
        Page<Booking> page = bookingRepository.searchForAdmin(
                safeRequest.getStatus(),
                safeRequest.getMentorUserId(),
                safeRequest.getMenteeUserId(),
                adminBookingPageable(safeRequest)
        );

        return PageResponse.<BookingResponse>builder()
                .content(page.getContent().stream().map(this::toBookingResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public BookingResponse getAdminBookingDetail(UUID bookingId) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        return toBookingResponse(booking);
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
        if (booking.getMentorProfile().getUser() == null
                || booking.getMentorProfile().getUser().getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện không còn hoạt động");
        }
        return booking;
    }

    private MentorAvailabilitySlot requireLockedSlotForDecision(Booking booking) {
        if (booking == null || booking.getSlot() == null || booking.getSlot().getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking hiện không gắn với khung giờ hợp lệ");
        }
        return mentorAvailabilitySlotRepository.findByIdForUpdate(booking.getSlot().getId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ mentoring"));
    }

    private void assertBookingAccess(Booking booking, UUID currentUserId) {
        boolean isMentee = booking.getMentee() != null && currentUserId.equals(booking.getMentee().getId());
        boolean isMentor = booking.getMentorProfile() != null && currentUserId.equals(booking.getMentorProfile().getUserId());
        if (!isMentee && !isMentor) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem booking này");
        }
    }

    private BookingResponse toBookingResponse(Booking booking) {
        User mentee = booking.getMentee();
        MentorProfile mentorProfile = booking.getMentorProfile();
        User mentorUser = mentorProfile == null ? null : mentorProfile.getUser();
        MentorService mentorService = booking.getService();
        MentorAvailabilitySlot slot = booking.getSlot();

        return BookingResponse.builder()
                .bookingId(booking.getId())
                .sessionId(booking.getId())
                .sessionStatus(booking.getStatus())
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
                .cancelReason(booking.getCancelReason())
                .meetingPlatform(booking.getMeetingPlatform())
                .meetingLink(booking.getMeetingLink())
                .location(booking.getLocation())
                .requestedStartTime(booking.getRequestedStartTime())
                .requestedEndTime(booking.getRequestedEndTime())
                .actualStartTime(booking.getActualStartTime())
                .actualEndTime(booking.getActualEndTime())
                .acceptedAt(booking.getAcceptedAt())
                .rejectedAt(booking.getRejectedAt())
                .cancelledAt(booking.getCancelledAt())
                .completedAt(booking.getCompletedAt())
                .mentorNote(booking.getMentorNote())
                .menteeNote(booking.getMenteeNote())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    private Pageable bookingPageable(BookingListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 20);
        Sort.Direction direction = request.resolveDirection();
        String sortBy = bookingSortBy(request.getSortBy());

        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private Pageable adminBookingPageable(AdminBookingListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 100);
        return PageRequest.of(page, size, Sort.by(request.resolveDirection(), bookingSortBy(request.getSortBy())));
    }

    private String bookingSortBy(String sortBy) {
        return switch (sortBy == null ? "" : sortBy.trim()) {
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            case "acceptedAt" -> "acceptedAt";
            case "rejectedAt" -> "rejectedAt";
            case "cancelledAt" -> "cancelledAt";
            case "completedAt" -> "completedAt";
            case "status" -> "status";
            default -> "requestedStartTime";
        };
    }

    private Booking getBookingForCancellation(UUID bookingId) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        return bookingRepository.findByIdForCancellation(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
    }

    private boolean isMentorOfBooking(Booking booking, UUID userId) {
        return userId != null
                && booking.getMentorProfile() != null
                && userId.equals(booking.getMentorProfile().getUserId());
    }

    private String requiredCancelReason(CancelBookingRequest request) {
        String reason = trimToNull(request == null ? null : request.cancelReason());
        if (reason == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Lý do hủy booking không được để trống");
        }
        return reason;
    }

    private long minutesUntilStart(Booking booking, LocalDateTime now) {
        if (booking.getRequestedStartTime() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian bắt đầu booking không hợp lệ");
        }
        return Duration.between(now, booking.getRequestedStartTime()).toMinutes();
    }

    private void applyMentorCancellationPenalty(MentorProfile mentorProfile, long minutesUntilStart, LocalDateTime now) {
        if (minutesUntilStart < MENTOR_SUSPENSION_CANCEL_DEADLINE_MINUTES) {
            mentorProfile.setBookingSuspendedUntil(now.plusDays(MENTOR_LATE_CANCEL_SUSPENSION_DAYS));
            return;
        }
        if (minutesUntilStart < MENTOR_SAFE_CANCEL_DEADLINE_MINUTES) {
            BigDecimal currentPenalty = mentorProfile.getLateCancellationPenaltyPoints() == null
                    ? BigDecimal.ZERO
                    : mentorProfile.getLateCancellationPenaltyPoints();
            mentorProfile.setLateCancellationPenaltyPoints(currentPenalty.add(MENTOR_LATE_CANCEL_PENALTY));
        }
    }

    private String cleanMeetingLink(String meetingLink) {
        String link = trimToNull(meetingLink);
        if (link == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "meetingLink không được để trống");
        }
        try {
            URI uri = URI.create(link);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "meetingLink phải là URL hợp lệ bắt đầu bằng http:// hoặc https://");
            }
            return link;
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "meetingLink phải là URL hợp lệ");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private void validateBookerEligibility(User mentee) {
        if (mentee.getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(ErrorCode.USER_INACTIVE, "Tài khoản hiện tại không ở trạng thái có thể tạo booking");
        }
        if (hasAnyRole(mentee, RoleCode.ADMIN, RoleCode.SYSTEM_ADMIN)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Tài khoản quản trị không được phép tạo booking");
        }
        if (!academicService.hasCompletedStudentProfile(mentee.getId())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn cần hoàn thành hồ sơ học thuật trước khi tạo booking");
        }
    }

    private boolean isDiscoverableMentorForBooking(MentorProfile mentorProfile) {
        return mentorProfile != null
                && mentorProfile.getStatus() == MentorStatus.ACTIVE
                && mentorProfile.getVerifiedAt() != null
                && mentorProfile.isAvailable()
                && trimToNull(mentorProfile.getHeadline()) != null
                && trimToNull(mentorProfile.getExpertiseDescription()) != null
                && mentorProfile.getTeachingMode() != null
                && mentorProfile.getSessionDuration() != null;
    }

    private boolean hasAnyRole(User user, RoleCode... roles) {
        if (user == null || user.getRoles() == null || user.getRoles().isEmpty() || roles == null) {
            return false;
        }
        for (RoleCode role : roles) {
            if (role != null && user.getRoles().contains(role)) {
                return true;
            }
        }
        return false;
    }

    private void releaseSlot(MentorAvailabilitySlot slot) {
        slot.setBooked(false);
    }

    private void deactivateSlot(MentorAvailabilitySlot slot) {
        slot.setBooked(false);
        slot.setActive(false);
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    @Transactional
    public void rejectAllPendingBookingsForMentor(UUID mentorUserId, String reason) {
        if (mentorUserId == null) {
            return;
        }
        List<Booking> pendingBookings = bookingRepository.findByMentorProfileUserIdAndStatus(mentorUserId, BookingStatus.PENDING);
        for (Booking booking : pendingBookings) {
            booking.setStatus(BookingStatus.REJECTED);
            booking.setRejectedAt(DateTimeUtil.now());
            booking.setRejectReason(reason);
            if (booking.getSlot() != null) {
                booking.getSlot().setBooked(false);
            }
        }
        bookingRepository.saveAll(pendingBookings);
    }

    @Transactional
    public BookingResponse cancelBookingByMentor(UUID mentorUserId, UUID bookingId, CancelBookingRequest request) {
        Booking booking = getBookingForCancellation(bookingId);
        if (!isMentorOfBooking(booking, mentorUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy booking này");
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor chỉ có thể hủy booking đã được chấp nhận");
        }

        LocalDateTime now = DateTimeUtil.now();
        long minutesUntilStart = minutesUntilStart(booking, now);
        if (minutesUntilStart <= 0) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking đã bắt đầu, không thể hủy bằng luồng này");
        }

        booking.setStatus(BookingStatus.CANCELLED_BY_MENTOR);
        booking.setCancelledAt(now);
        booking.setCancelReason(requiredCancelReason(request));

        MentorAvailabilitySlot slot = booking.getSlot();
        if (slot != null) {
            deactivateSlot(slot);
        }

        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            applyMentorCancellationPenalty(mentorProfile, minutesUntilStart, now);
        }

        return toBookingResponse(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse cancelBookingByMentee(UUID menteeId, UUID bookingId, CancelBookingRequest request) {
        Booking booking = getBookingForCancellation(bookingId);
        if (menteeId == null || booking.getMentee() == null || !menteeId.equals(booking.getMentee().getId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy booking này");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể hủy booking đang chờ phản hồi hoặc đã được chấp nhận");
        }

        LocalDateTime now = DateTimeUtil.now();
        BookingStatus currentStatus = booking.getStatus();
        long minutesUntilStart = currentStatus == BookingStatus.ACCEPTED
                ? minutesUntilStart(booking, now)
                : Long.MAX_VALUE;

        booking.setStatus(BookingStatus.CANCELLED_BY_MENTEE);
        booking.setCancelledAt(now);
        booking.setCancelReason(requiredCancelReason(request));

        MentorAvailabilitySlot slot = booking.getSlot();
        if (slot != null && currentStatus == BookingStatus.ACCEPTED) {
            if (minutesUntilStart >= MENTEE_ACCEPTED_CANCEL_RELEASE_DEADLINE_MINUTES) {
                releaseSlot(slot);
            } else {
                deactivateSlot(slot);
            }
        }

        return toBookingResponse(bookingRepository.save(booking));
    }
}





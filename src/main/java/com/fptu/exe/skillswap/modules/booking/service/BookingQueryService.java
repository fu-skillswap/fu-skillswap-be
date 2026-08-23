package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.dto.BookingViewRole;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingQueryService {

    private static final List<BookingStatus> MENTEE_PRIMARY_ACTION_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
            BookingStatus.AWAITING_MENTOR_COMPLETION,
            BookingStatus.AWAITING_MENTEE_CONFIRMATION,
            BookingStatus.UNDER_REVIEW
    );
    private static final List<BookingStatus> MENTEE_SECONDARY_ACTION_STATUSES = List.of(BookingStatus.PENDING);
    private static final List<BookingStatus> MENTEE_UPCOMING_STATUSES = List.of(BookingStatus.PAID);
    private static final List<BookingStatus> MENTOR_PRIMARY_ACTION_STATUSES = List.of(
            BookingStatus.PENDING,
            BookingStatus.AWAITING_MENTOR_COMPLETION,
            BookingStatus.UNDER_REVIEW
    );
    private static final List<BookingStatus> MENTOR_SECONDARY_ACTION_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT
    );
    private static final List<BookingStatus> MENTOR_UPCOMING_STATUSES = List.of(
            BookingStatus.PAID,
            BookingStatus.AWAITING_MENTEE_CONFIRMATION
    );

    private static final List<BookingStatus> BOOKING_LIST_CANCELLED_PRIORITY_STATUSES = List.of(
            BookingStatus.CANCELLED_BY_MENTEE,
            BookingStatus.CANCELLED_BY_MENTOR
    );

    private final BookingRepository bookingRepository;
    private final SessionService sessionService;
    private final ConversationService conversationService;
    private final PaymentOrderRepository paymentOrderRepository;
    private final BookingResponseMapper bookingResponseMapper;

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMyBookings(UUID currentUserId, BookingListRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        BookingListRequest safeRequest = request == null ? new BookingListRequest() : request;
        BookingViewRole role = safeRequest.getRole() == null ? BookingViewRole.MENTEE : safeRequest.getRole();
        Pageable pageable = safeRequest.getStatus() == null
                ? bookingPriorityPageable(safeRequest)
                : bookingPageable(safeRequest);

        LocalDateTime startTimeStart = DateTimeUtil.now().minusDays(7);
        LocalDateTime startTimeEnd = DateTimeUtil.now().plusDays(90);

        Page<Booking> page = switch (role) {
            case MENTEE -> safeRequest.getStatus() == null
                    ? bookingRepository.findMyMenteeBookingsOrderedByDashboardPriority(
                            currentUserId,
                            MENTEE_PRIMARY_ACTION_STATUSES,
                            MENTEE_SECONDARY_ACTION_STATUSES,
                            MENTEE_UPCOMING_STATUSES,
                            BOOKING_LIST_CANCELLED_PRIORITY_STATUSES,
                            startTimeStart,
                            startTimeEnd,
                            pageable
                    )
                    : bookingRepository.findByMenteeIdAndStatus(currentUserId, safeRequest.getStatus(), pageable);
            case MENTOR -> safeRequest.getStatus() == null
                    ? bookingRepository.findMyMentorBookingsOrderedByDashboardPriority(
                            currentUserId,
                            MENTOR_PRIMARY_ACTION_STATUSES,
                            MENTOR_SECONDARY_ACTION_STATUSES,
                            MENTOR_UPCOMING_STATUSES,
                            BOOKING_LIST_CANCELLED_PRIORITY_STATUSES,
                            startTimeStart,
                            startTimeEnd,
                            pageable
                    )
                    : bookingRepository.findByMentorProfileUserIdAndStatus(currentUserId, safeRequest.getStatus(), pageable);
        };

        List<UUID> bookingIds = page.getContent().stream().map(Booking::getId).toList();
        Map<UUID, UUID> bookingToConvMap = conversationService != null
                ? conversationService.findConversationIdsForBookings(page.getContent())
                : Collections.emptyMap();
        Map<UUID, Session> sessionsByBookingId = sessionService != null
                ? sessionService.findByBookingIds(bookingIds)
                : Collections.emptyMap();
        Map<UUID, PaymentOrder> paymentOrdersByBookingId =
                bookingIds.isEmpty() || paymentOrderRepository == null
                        ? Collections.emptyMap()
                        : paymentOrderRepository.findByTargetTypeAndTargetIdIn(PaymentTargetType.BOOKING, bookingIds).stream()
                        .collect(Collectors.toMap(
                                PaymentOrder::getTargetId,
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new
                        ));

        return PageResponse.<BookingResponse>builder()
                .content(page.getContent().stream()
                        .map(b -> bookingResponseMapper.toBookingResponse(b, bookingToConvMap, sessionsByBookingId, paymentOrdersByBookingId))
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
        return bookingResponseMapper.toBookingResponse(booking);
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

        List<UUID> bookingIds = page.getContent().stream().map(Booking::getId).toList();
        Map<UUID, UUID> bookingToConvMap = conversationService != null
                ? conversationService.findConversationIdsForBookings(page.getContent())
                : Collections.emptyMap();
        Map<UUID, Session> sessionsByBookingId = sessionService != null
                ? sessionService.findByBookingIds(bookingIds)
                : Collections.emptyMap();

        return PageResponse.<BookingResponse>builder()
                .content(page.getContent().stream().map(b -> bookingResponseMapper.toBookingResponse(b, bookingToConvMap, sessionsByBookingId)).toList())
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
        return bookingResponseMapper.toBookingResponse(booking);
    }

    private void assertBookingAccess(Booking booking, UUID currentUserId) {
        boolean isMentee = booking.getMentee() != null && currentUserId.equals(booking.getMentee().getId());
        boolean isMentor = booking.getMentorProfile() != null && currentUserId.equals(booking.getMentorProfile().getUserId());
        if (!isMentee && !isMentor) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem booking này");
        }
    }

    private Pageable bookingPriorityPageable(BookingListRequest request) {
        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(request.getSize(), 100));
        return PageRequest.of(page, size);
    }

    private Pageable bookingPageable(BookingListRequest request) {
        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(request.getSize(), 100));
        String sortDir = request.getDirection() == null ? "desc" : request.getDirection().toLowerCase();
        Sort.Direction direction = "asc".equals(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortBy = normalizeBookingSortBy(request.getSortBy());
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private Pageable adminBookingPageable(AdminBookingListRequest request) {
        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(request.getSize(), 100));
        String sortDir = request.getDirection() == null ? "desc" : request.getDirection().toLowerCase();
        Sort.Direction direction = "asc".equals(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortBy = normalizeAdminBookingSortBy(request.getSortBy());
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private String normalizeBookingSortBy(String sortBy) {
        if (sortBy == null) {
            return "createdAt";
        }
        return switch (sortBy) {
            case "updatedAt" -> "updatedAt";
            case "selectedStartTime" -> "selectedStartTime";
            case "selectedEndTime" -> "selectedEndTime";
            case "acceptedAt" -> "acceptedAt";
            case "rejectedAt" -> "rejectedAt";
            case "cancelledAt" -> "cancelledAt";
            case "completedAt" -> "completedAt";
            case "finalizedAt" -> "finalizedAt";
            default -> "createdAt";
        };
    }

    private String normalizeAdminBookingSortBy(String sortBy) {
        if (sortBy == null) {
            return "createdAt";
        }
        return switch (sortBy) {
            case "updatedAt" -> "updatedAt";
            case "selectedStartTime" -> "selectedStartTime";
            case "selectedEndTime" -> "selectedEndTime";
            case "acceptedAt" -> "acceptedAt";
            case "rejectedAt" -> "rejectedAt";
            case "cancelledAt" -> "cancelledAt";
            case "completedAt" -> "completedAt";
            case "finalizedAt" -> "finalizedAt";
            case "issueSubmittedAt" -> "issueSubmittedAt";
            default -> "createdAt";
        };
    }
}

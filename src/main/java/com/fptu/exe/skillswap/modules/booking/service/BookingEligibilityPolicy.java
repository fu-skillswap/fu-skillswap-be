package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.port.BookingEligibilityQueryPort;
import com.fptu.exe.skillswap.shared.port.ContentEntitlementQuery;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.identity.port.AcademicEligibilityQuery;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingCapability;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class BookingEligibilityPolicy implements ContentEntitlementQuery, BookingEligibilityQueryPort {

    private final AcademicEligibilityQuery academicEligibilityQuery;
    private final BookingRepository bookingRepository;

    @Autowired
    public BookingEligibilityPolicy(AcademicEligibilityQuery academicEligibilityQuery, BookingRepository bookingRepository) {
        this.academicEligibilityQuery = academicEligibilityQuery;
        this.bookingRepository = bookingRepository;
    }

    /** Compatibility constructor for existing pure unit tests. */
    @Deprecated(forRemoval = true)
    public BookingEligibilityPolicy(AcademicEligibilityQuery academicEligibilityQuery) {
        this(academicEligibilityQuery, null);
    }

    /** Shared paid-service entitlement policy for premium content and notifications. */
    @Override
    public boolean hasServiceContentEntitlement(UUID viewerId, UUID serviceId) {
        if (viewerId == null || serviceId == null || bookingRepository == null) return false;
        return bookingRepository.existsByMenteeUserIdAndServiceIdAndStatusIn(viewerId, serviceId, serviceContentEntitlementStatuses());
    }

    /** Notification and content modules ask the policy for recipients rather than duplicating lifecycle status rules. */
    @Override
    public Set<UUID> findUsersWithServiceContentEntitlement(Collection<UUID> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty() || bookingRepository == null) return Set.of();
        return new LinkedHashSet<>(bookingRepository.findDistinctMenteeIdsByServiceIdsAndStatusIn(serviceIds, serviceContentEntitlementStatuses()));
    }

    public void validateBookerEligibility(UserSummaryRecord mentee) {
        if (mentee == null || !mentee.isActive()) {
            throw new BaseException(ErrorCode.USER_INACTIVE, "Tài khoản hiện tại không ở trạng thái có thể tạo booking");
        }
        if (hasAnyRole(mentee, RoleCode.ADMIN, RoleCode.SYSTEM_ADMIN)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Tài khoản quản trị không được phép tạo booking");
        }
        if (!academicEligibilityQuery.hasCompletedStudentProfile(mentee.userId())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn cần hoàn thành hồ sơ học thuật trước khi tạo booking");
        }
    }

    public boolean isDiscoverableMentorForBooking(MentorBookingCapability capability) {
        return capability != null
                && capability.isActiveMentor()
                && capability.available()
                && capability.hasCompletedProfile();
    }

    public boolean isPublicBookingOfferAvailable(
            MentorBookingCapability capability,
            boolean hasActiveBookableService,
            LocalDateTime now
    ) {
        if (!hasActiveBookableService || !isDiscoverableMentorForBooking(capability)) {
            return false;
        }
        return capability.canAcceptBookings(now);
    }

    private boolean hasAnyRole(UserSummaryRecord user, RoleCode... roles) {
        if (user == null || user.roles() == null || user.roles().isEmpty() || roles == null) {
            return false;
        }
        for (RoleCode role : roles) {
            if (role != null && user.roles().contains(role)) {
                return true;
            }
        }
        return false;
    }

    private Set<BookingStatus> serviceContentEntitlementStatuses() {
        return Set.of(BookingStatus.PAID,
                BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.AWAITING_MENTEE_CONFIRMATION,
                BookingStatus.COMPLETED);
    }
}

package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BookingEligibilityPolicy {

    private final UserQueryPort userQueryPort;
    private final BookingRepository bookingRepository;

    public boolean hasServiceContentEntitlement(UUID viewerId, UUID serviceId) {
        if (viewerId == null || serviceId == null || bookingRepository == null) return false;
        return bookingRepository.existsByMenteeIdAndServiceIdAndStatusIn(viewerId, serviceId, serviceContentEntitlementStatuses());
    }

    public Set<UUID> findUsersWithServiceContentEntitlement(Collection<UUID> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty() || bookingRepository == null) return Set.of();
        return new LinkedHashSet<>(bookingRepository.findDistinctMenteeIdsByServiceIdsAndStatusIn(serviceIds, serviceContentEntitlementStatuses()));
    }

    public void validateBookerEligibility(User mentee) {
        if (mentee.getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(ErrorCode.USER_INACTIVE, "Tài khoản hiện tại không ở trạng thái có thể tạo booking");
        }
        if (hasAnyRole(mentee, RoleCode.ADMIN, RoleCode.SYSTEM_ADMIN)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Tài khoản quản trị không được phép tạo booking");
        }
        if (userQueryPort != null && !userQueryPort.hasCompletedStudentProfile(mentee.getId())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn cần hoàn thành hồ sơ học thuật trước khi tạo booking");
        }
    }

    public boolean isDiscoverableMentorForBooking(MentorProfile mentorProfile) {
        return mentorProfile != null
                && mentorProfile.getStatus() == MentorStatus.ACTIVE
                && mentorProfile.getVerifiedAt() != null
                && mentorProfile.isAvailable()
                && trimToNull(mentorProfile.getHeadline()) != null
                && trimToNull(mentorProfile.getExpertiseDescription()) != null
                && mentorProfile.getFoundationSupportLevel() != null
                && mentorProfile.getOutputReviewSupportLevel() != null
                && mentorProfile.getDirectionSupportLevel() != null;
    }

    public boolean isPublicBookingOfferAvailable(
            MentorProfile mentorProfile,
            boolean hasActiveBookableService,
            LocalDateTime now
    ) {
        if (!hasActiveBookableService || !isDiscoverableMentorForBooking(mentorProfile)) {
            return false;
        }
        LocalDateTime suspendedUntil = mentorProfile.getBookingSuspendedUntil();
        return suspendedUntil == null || now == null || !suspendedUntil.isAfter(now);
    }

    public boolean canMenteeRequestBooking(UUID menteeUserId, MentorProfile mentorProfile) {
        if (mentorProfile == null || !isDiscoverableMentorForBooking(mentorProfile)) {
            return false;
        }
        if (menteeUserId == null) {
            return false;
        }
        if (mentorProfile.getUser() != null && menteeUserId.equals(mentorProfile.getUser().getId())) {
            return false;
        }
        return userQueryPort == null || userQueryPort.hasCompletedStudentProfile(menteeUserId);
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private Set<BookingStatus> serviceContentEntitlementStatuses() {
        return Set.of(BookingStatus.PAID,
                BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.AWAITING_MENTEE_CONFIRMATION,
                BookingStatus.COMPLETED);
    }
}

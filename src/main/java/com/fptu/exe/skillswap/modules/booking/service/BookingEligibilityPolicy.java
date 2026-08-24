package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.identity.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class BookingEligibilityPolicy {

    private final AcademicService academicService;
    private final BookingRepository bookingRepository;

    @Autowired
    public BookingEligibilityPolicy(AcademicService academicService, BookingRepository bookingRepository) {
        this.academicService = academicService;
        this.bookingRepository = bookingRepository;
    }

    /** Compatibility constructor for existing pure unit tests. */
    @Deprecated(forRemoval = true)
    public BookingEligibilityPolicy(AcademicService academicService) {
        this(academicService, null);
    }

    /** Resource modules depend on this policy, never on raw booking-status values. */
    public boolean canAccessServiceResources(java.util.UUID viewerId, java.util.UUID serviceId) {
        if (viewerId == null || serviceId == null || bookingRepository == null) return false;
        return bookingRepository.existsByMenteeIdAndServiceIdAndStatusIn(viewerId, serviceId, serviceResourceAccessStatuses());
    }

    /** Notification and content modules ask the policy for recipients rather than duplicating lifecycle status rules. */
    public java.util.Set<java.util.UUID> findUsersWithServiceResourceAccess(java.util.Collection<java.util.UUID> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty() || bookingRepository == null) return java.util.Set.of();
        return new java.util.LinkedHashSet<>(bookingRepository.findDistinctMenteeIdsByServiceIdsAndStatusIn(serviceIds, serviceResourceAccessStatuses()));
    }

    public void validateBookerEligibility(User mentee) {
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

    /**
     * Public profile capability only. Requester-specific eligibility remains enforced by booking
     * quote/create flows because a public mentor detail may have no authenticated viewer.
     */
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

    private java.util.Set<BookingStatus> serviceResourceAccessStatuses() {
        return java.util.Set.of(BookingStatus.PAID,
                BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.AWAITING_MENTEE_CONFIRMATION,
                BookingStatus.COMPLETED);
    }
}

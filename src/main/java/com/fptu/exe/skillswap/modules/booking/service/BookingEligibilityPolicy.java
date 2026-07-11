package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingEligibilityPolicy {

    private final AcademicService academicService;

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
}

package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryAcademicProfileResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryActivityResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryMentorProfileResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryResponse;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPort;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.dto.UserSummaryAcademicDto;
import com.fptu.exe.skillswap.modules.mentor.port.MentorAdminPort;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorSummaryProfileDto;
import com.fptu.exe.skillswap.modules.payment.port.PaymentAdminPort;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserSummaryService {

    private final UserQueryPort userQueryPort;
    private final UserAdminPort userAdminPort;
    private final MentorAdminPort mentorAdminPort;
    private final BookingQueryPort bookingQueryPort;
    private final PaymentAdminPort paymentAdminPort;
    private final ForumAdminPort forumAdminPort;

    public AdminUserSummaryResponse getSummary(UUID userId) {
        User user = userQueryPort.findAdminVisibleUserById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy user visible"));

        return new AdminUserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getStatus().name(),
                extractVisibleRoles(user),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                toAcademicSummary(userAdminPort.getAcademicProfileSummary(userId)),
                toMentorSummary(mentorAdminPort.getMentorProfileSummary(userId)),
                new AdminUserSummaryActivityResponse(
                        bookingQueryPort.countByMenteeId(userId),
                        bookingQueryPort.countByMentorProfileUserId(userId),
                        paymentAdminPort.countTotalPaymentOrdersByUserId(userId),
                        paymentAdminPort.countTotalPayoutRequestsByUserId(userId),
                        forumAdminPort.countReportsCreatedBy(userId)
                )
        );
    }

    private AdminUserSummaryAcademicProfileResponse toAcademicSummary(UserSummaryAcademicDto dto) {
        if (dto == null) {
            return null;
        }
        return new AdminUserSummaryAcademicProfileResponse(
                dto.studentCode(),
                dto.campusCode(),
                dto.campusName(),
                dto.programCode(),
                dto.programName(),
                dto.specializationCode(),
                dto.specializationName(),
                dto.semester(),
                dto.isAlumni()
        );
    }

    private AdminUserSummaryMentorProfileResponse toMentorSummary(MentorSummaryProfileDto dto) {
        if (dto == null) {
            return null;
        }
        return new AdminUserSummaryMentorProfileResponse(
                dto.exists(),
                dto.mentorStatus(),
                dto.isAvailable(),
                dto.verifiedAt(),
                dto.headline(),
                dto.averageRating(),
                dto.totalCompletedSessions()
        );
    }

    private List<String> extractVisibleRoles(User user) {
        List<String> roles = new ArrayList<>();
        if (user.getRoles() == null) {
            return roles;
        }
        if (user.getRoles().contains(RoleCode.MENTEE)) {
            roles.add(RoleCode.MENTEE.name());
        }
        if (user.getRoles().contains(RoleCode.MENTOR)) {
            roles.add(RoleCode.MENTOR.name());
        }
        return roles;
    }
}
